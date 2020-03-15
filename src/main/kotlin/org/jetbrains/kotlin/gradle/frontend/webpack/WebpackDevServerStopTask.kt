package org.jetbrains.kotlin.gradle.frontend.webpack

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import org.jetbrains.kotlin.gradle.frontend.npm.NpmExtension
import org.jetbrains.kotlin.gradle.frontend.util.frontendExtension
import org.jetbrains.kotlin.gradle.frontend.util.mkdirsOrFail
import org.jetbrains.kotlin.gradle.frontend.util.nodePath
import org.jetbrains.kotlin.gradle.frontend.util.startWithRedirectOnFail

open class WebpackDevServerStopTask : DefaultTask() {
    @TaskAction
    fun stop() {
        val processBuilderCommands = if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            arrayListOf("taskkill", "/f", "/im", "node")
        } else {
            arrayListOf("killall", "-9", "node")
        }
        ProcessBuilder(processBuilderCommands)
                .directory(project.rootProject.buildDir)
                .start()
    }
}