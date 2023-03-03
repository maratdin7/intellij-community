// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.kotlin.idea.notification.showCoroutinesCounterNotification

internal class CoroutinesCounterActivity : ProjectActivity {
    companion object {
        var enableCoroutineCounter = false
    }

    override suspend fun execute(project: Project) {
        enableCoroutineCounter = false

        smartReadAction(project) {
            showCoroutinesCounterNotification(project, CoroutinesCounterService.getInstance(project))
        }
    }
}