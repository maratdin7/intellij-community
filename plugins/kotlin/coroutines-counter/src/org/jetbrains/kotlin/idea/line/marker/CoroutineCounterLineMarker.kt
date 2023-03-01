// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.line.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.CoroutinesCounterService
import org.jetbrains.kotlin.idea.CoroutinesCounterActivity.Companion.enableCoroutineCounter
import org.jetbrains.kotlin.psi.KtNamedFunction
import javax.swing.Icon

class CoroutinesCounterLineMarker : LineMarkerProviderDescriptor() {
    override fun getName() = "Coroutine counter line marker"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (enableCoroutineCounter.not()) return

        val project = elements.firstOrNull()?.project ?: return
        val coroutinesCounter = CoroutinesCounterService.getInstance(project)

        elements.filterIsInstance<KtNamedFunction>().forEach { ktNamedFunction ->
            val expr = coroutinesCounter.getNumOfCoroutines(ktNamedFunction)
            val anchor = ktNamedFunction.nameIdentifier ?: ktNamedFunction
            result += CoroutinesCounterLineMarkerInfo(
                anchor,
                expr.toString(),
                expr.icon,
                ktNamedFunction.text,
            )
        }
    }
}

private class CoroutinesCounterLineMarkerInfo(
    anchor: PsiElement,
    message: String,
    icon: Icon,
    @NlsSafe private val declarationName: String,
) : MergeableLineMarkerInfo<PsiElement>(
    /* element = */ anchor,
    /* textRange = */ anchor.textRange,
    /* icon = */ icon,
    /* tooltipProvider = */ { message },
    /* navHandler = */ null,
    /* alignment = */ GutterIconRenderer.Alignment.RIGHT,
    /* accessibleNameProvider = */ { message }
) {
    override fun createGutterRenderer() = LineMarkerGutterIconRenderer(this)
    override fun getElementPresentation(element: PsiElement) = declarationName

    override fun canMergeWith(info: MergeableLineMarkerInfo<*>) = info is CoroutinesCounterLineMarkerInfo
    override fun getCommonIcon(infos: List<MergeableLineMarkerInfo<*>>) = infos.firstNotNullOf { it.icon }
}