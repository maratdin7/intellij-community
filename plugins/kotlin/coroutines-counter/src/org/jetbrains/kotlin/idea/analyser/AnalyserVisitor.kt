// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.analyser

import org.jetbrains.kotlin.idea.analyser.expression.Expression
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments

class AnalyserVisitor(private val map: MutableMap<KtFunction, Expression>) : KtVisitor<Expression, Nothing?>() {
    override fun visitKtElement(element: KtElement, data: Nothing?): Expression {
        var child = element.firstChild
        var numOfCoroutines: Expression = Expression.TerminatedExpression.Constant.ZERO
        while (child != null) {
            if (child is KtElement) {
                numOfCoroutines += child.accept(this, data)
            }
            child = child.getNextSiblingIgnoringWhitespaceAndComments()
        }
        return numOfCoroutines
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression, data: Nothing?): Expression {
        return Expression.TerminatedExpression.Constant.ZERO
    }

    override fun visitNamedFunction(function: KtNamedFunction, data: Nothing?): Expression {
        return map.getOrPut(function) { visitKtElement(function, data) }
    }

    override fun visitLambdaExpression(expression: KtLambdaExpression, data: Nothing?): Expression = Expression.TerminatedExpression.Constant.ZERO

    override fun visitTryExpression(expression: KtTryExpression, data: Nothing?): Expression {
        return Expression.TerminatedExpression.SomeCoroutines
    }

    override fun visitWhenExpression(expression: KtWhenExpression, data: Nothing?): Expression =
        expression.run {
            val entriesExpr = checkBranches(entries) ?: Expression.TerminatedExpression.SomeCoroutines
            val subjExpr = subjectExpression?.accept(this@AnalyserVisitor, data) ?: Expression.TerminatedExpression.Constant.ZERO
            subjExpr + entriesExpr
        }

    override fun visitWhenEntry(ktWhenEntry: KtWhenEntry, data: Nothing?): Expression =
        ktWhenEntry.run {
            val condExpr = conditions.all { it.accept(this@AnalyserVisitor, data) == Expression.TerminatedExpression.Constant.ZERO }
            if (condExpr) expression?.accept(this@AnalyserVisitor, data) ?: Expression.TerminatedExpression.Constant.ZERO
            else Expression.TerminatedExpression.SomeCoroutines
        }

    override fun visitIfExpression(expression: KtIfExpression, data: Nothing?): Expression =
        expression.run {
            val branchExpr = checkBranches(listOf(then, `else`)) ?: return Expression.TerminatedExpression.SomeCoroutines
            val condExpr = condition?.accept(this@AnalyserVisitor, data) ?: Expression.TerminatedExpression.Constant.ZERO
            condExpr + branchExpr
        }

    private fun checkBranches(branches: List<KtElement?>): Expression? {
        val set = branches.mapNotNullTo(mutableSetOf()) {
            it?.accept(this, null)
        }
        if (set.isEmpty()) return Expression.TerminatedExpression.Constant.ZERO

        return set.first().takeIf { set.size == 1 }
    }

    override fun visitLoopExpression(loopExpression: KtLoopExpression, data: Nothing?): Expression {
        return Expression.TerminatedExpression.SomeCoroutines
    }

    override fun visitCallExpression(expression: KtCallExpression, data: Nothing?): Expression {
        analyze(expression) {
            val reference = expression.calleeExpression?.mainReference
            val ktFunction = reference?.resolve() as? KtFunction ?: return Expression.TerminatedExpression.SomeCoroutines

            val name = ktFunction.fqName?.asString() ?: ""
            val withLambda = analyseWithLambda(name)

            val functionCall =
                expression.resolveCall().successfulFunctionCallOrNull() ?: return Expression.TerminatedExpression.SomeCoroutines

            val argsKtExpr = functionCall.argumentMapping.keys

            val argsExpr = argsKtExpr.fold(Expression.TerminatedExpression.Constant.ZERO as Expression) { acc, ktExpression ->
                val expr =
                    if (withLambda && ktExpression is KtLambdaExpression) visitKtElement(ktExpression, data)
                    else ktExpression.accept(this@AnalyserVisitor, data)

                acc + expr
            }

            val coroutinesInFunction = if (isCreateCoroutine(name)) Expression.TerminatedExpression.Constant.ONE else Expression.Fun(ktFunction)

            return coroutinesInFunction + argsExpr
        }
    }

    companion object {
        private const val coroutinePackage = "kotlinx.coroutines"
        private const val kotlinStdlibPackage = "kotlin"

        private val stdlibFunWhereLambdaGuaranteedWillExecuted =
            listOf("let", "also", "takeIf", "takeUnless", "apply", "run")

        private val coroutineFunWhereLambdaGuaranteedWillExecuted =
            listOf("coroutineScope", "withContext")

        private val funCreatedCoroutines = listOf("launch", "async", "runBlocking", "start", "actor", "broadcast", "produce")

        private fun String.isNameAllowed(prefix: String, listOfNames: Collection<String>): Boolean =
            startsWith(prefix) && listOfNames.any { it in this }

        fun isCreateCoroutine(name: String): Boolean {
            return name.isNameAllowed(coroutinePackage, funCreatedCoroutines)
        }

        fun analyseWithLambda(name: String): Boolean =
            name.run {
                isNameAllowed(kotlinStdlibPackage, stdlibFunWhereLambdaGuaranteedWillExecuted)
                        || isNameAllowed(coroutinePackage, funCreatedCoroutines)
                        || isNameAllowed(coroutinePackage, coroutineFunWhereLambdaGuaranteedWillExecuted)
            }
    }
}