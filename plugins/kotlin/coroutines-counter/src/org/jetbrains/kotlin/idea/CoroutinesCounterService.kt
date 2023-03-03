// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.psi.search.FileTypeIndex
import org.jetbrains.kotlin.idea.analyser.AnalyserVisitor
import org.jetbrains.kotlin.idea.analyser.expression.Expression
import org.jetbrains.kotlin.idea.analyser.expression.Expression.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.findLibrary
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.namedFunctionRecursiveVisitor
import java.util.concurrent.ConcurrentHashMap

class CoroutinesCounterService(private val project: Project) {
    companion object {
        private val stages = listOf<CoroutinesCounterService.(ktFunction: KtFunction) -> Unit>(
            CoroutinesCounterService::analyseFunction,
            CoroutinesCounterService::computeNumOfCoroutinesInFunction
        )


        private fun Module.hasCoroutines(): Boolean {
            val findLibrary = findLibrary {
                "kotlinx-coroutines-core" in it.presentableName
            }
            return findLibrary != null
        }

        fun getInstance(project: Project): CoroutinesCounterService = runReadAction {
            if (project.isDisposed) throw ProcessCanceledException() else project.service()
        }
    }

    private val modulesWithCoroutinesApi: Map<Module, Collection<KtFunction>> by lazy {
        project.modules.filter { it.hasCoroutines() }
            .associateWith { module ->
                FileTypeIndex.getFiles(KotlinFileType.INSTANCE, module.moduleScope)
                    .mapNotNull { it.toPsiFile(project) }
                    .filterIsInstance<KtFile>()
                    .flatMapTo(mutableSetOf()) { ktFile ->            // TODO: Maybe parallel?
                        mutableListOf<KtFunction>().also { fileFunctions ->
                            ktFile.accept(namedFunctionRecursiveVisitor { ktNamedFunction ->
                                fileFunctions += ktNamedFunction
                            })
                        }
                    }
            }
    }

    private val concurrentMap = ConcurrentHashMap<KtFunction, Expression>()

    private val analyserVisitor = AnalyserVisitor(concurrentMap)

    fun numOfModulesWithCoroutines() = modulesWithCoroutinesApi.keys.size

    fun getNumOfCoroutines(ktNamedFunction: KtNamedFunction): TerminatedExpression {
        val expr = concurrentMap.computeIfAbsent(ktNamedFunction) { Fun(ktNamedFunction) }
        return expr.compute()
    }

    fun coroutinesCounter() {
        suspend fun <T> List<T>.runParallelWithProgress(title: String, block: suspend (v: T) -> Unit) {
            withBackgroundProgress(project, title, true) {
                this@runParallelWithProgress.forEachWithProgress(true, block)
            }
        }

        runBackgroundableTask(KotlinBundle.message("kotlin.coroutines.counter.title")) { progressIndicator ->
            runBlockingCancellable {
                smartReadAction(project) {
                    runBlockingCancellable {
                        modulesWithCoroutinesApi.map { it.value }.run {
                            stages.forEachIndexed { i, stage ->
                                runParallelWithProgress(
                                    KotlinBundle.message(
                                        "kotlin.coroutines.counter.progress.bar.title",
                                        i + 1,
                                        stages.size
                                    )
                                ) { ktFunctions -> ktFunctions.forEach { this@CoroutinesCounterService.stage(it) } }

                                progressIndicator.fraction = i.toDouble() / stages.size
                            }
                        }
                    }
                }
            }
        }
    }

    private fun analyseFunction(ktFunction: KtFunction) {
        ktFunction.accept(analyserVisitor, null)
    }

    private fun computeNumOfCoroutinesInFunction(ktFunction: KtFunction) {
        concurrentMap.computeIfPresent(ktFunction) { _, expr -> expr.compute() } ?: Fun(ktFunction).compute()
    }

    private fun Expression.compute(set: MutableSet<KtFunction> = mutableSetOf()): TerminatedExpression {
        when (this) {
            is Bin -> {
                val (l, op, r) = this
                val (leftAns, rightAns) = l.compute(set) to r.compute(set)

                return when (op) {
                    Operation.PLUS -> {
                        (leftAns + rightAns) as TerminatedExpression
                    }

                    Operation.TIMES -> TODO("A more complex analysis of the conditions is required")
                }
            }

            is Fun -> {
                if (ktFunction in set) return TerminatedExpression.Infinity

                val expr = concurrentMap.getOrPut(this.ktFunction) { ktFunction.accept(analyserVisitor, null) }
                set += ktFunction
                val terminatedExpr = expr.compute(set)
                set.remove(ktFunction)

                return terminatedExpr
            }

            is TerminatedExpression -> return this
        }
    }
}