// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.notification

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import org.jetbrains.kotlin.idea.CoroutinesCounterActivity
import org.jetbrains.kotlin.idea.CoroutinesCounterService
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

fun showCoroutinesCounterNotification(project: Project, coroutinesCounter: CoroutinesCounterService) {
    //RunOnceUtil.runOnceForProject(project, "kotlin.coroutines.counter.was.shown.once") {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Coroutines counter")
        .createNotification(
            KotlinBundle.message("kotlin.coroutines.counter.title"),
            KotlinBundle.message(
                "kotlin.coroutines.counter.message.text",
                coroutinesCounter.numOfModulesWithCoroutines(),
                project.modules.size
            ),
            NotificationType.INFORMATION
        )
        .addAction(
            object : NotificationAction("Yes") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    coroutinesCounter.coroutinesCounter()
                    CoroutinesCounterActivity.enableCoroutineCounter = true
                    notification.expire()
                }
            }
        )
        .addAction(object : NotificationAction("No") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                notification.expire()
            }
        })
        .setSuggestionType(true)
        .setIcon(KotlinIcons.SMALL_LOGO)
        .setImportant(true)
        .notify(project)
    //}
}


