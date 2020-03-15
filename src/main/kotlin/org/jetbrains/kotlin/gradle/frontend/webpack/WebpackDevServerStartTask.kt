package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.frontend.npm.NpmExtension
import org.jetbrains.kotlin.gradle.frontend.util.frontendExtension
import org.jetbrains.kotlin.gradle.frontend.util.mkdirsOrFail
import org.jetbrains.kotlin.gradle.frontend.util.nodePath

open class WebpackDevServerStartTask : DefaultTask() {
    private val npm = project.extensions.getByType(NpmExtension::class.java)

    @get:Nested
    val config by lazy {
        project.frontendExtension.bundles().filterIsInstance<WebPackExtension>().singleOrNull()
                ?: throw GradleException("Only one webpack bundle is supported")
    }

    @InputFile
    val webPackConfigFile = (config.webpackConfigFile?.let {
        project.file(it)
    } ?: project.buildDir.resolve("webpack.config.js")).apply {
        parentFile.mkdirsOrFail()
        if (!exists()) createNewFile()
    }

    @TaskAction
    fun start() {
        val nodePath = nodePath(project.rootProject, "node").first().absolutePath
        val webpackDevServerPath = npm.nodeModulesDir.resolve("webpack-dev-server/bin/webpack-dev-server.js").absolutePath
        val configFile = webPackConfigFile.absolutePath
        val processBuilderCommands = arrayListOf(
                nodePath,
                webpackDevServerPath,
                "--config", configFile
        )
        ProcessBuilder(processBuilderCommands)
                .directory(project.rootProject.buildDir)
                .start()
    }
}