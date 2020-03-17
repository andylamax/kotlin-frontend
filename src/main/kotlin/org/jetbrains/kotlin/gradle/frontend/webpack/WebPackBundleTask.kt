package org.jetbrains.kotlin.gradle.frontend.webpack

import groovy.json.JsonSlurper
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.npm.NpmExtension
import org.jetbrains.kotlin.gradle.frontend.util.*

/**
 * @author Sergey Mashkov
 */
open class WebPackBundleTask : DefaultTask() {
    private val npm = project.extensions.getByType(NpmExtension::class.java)

    private val config by lazy {
        project.frontendExtension.bundles().filterIsInstance<WebPackExtension>().singleOrNull()
                ?: throw GradleException("Only one webpack bundle is supported")
    }

    @get:InputFile
    val webPackConfigFile by lazy {
        config.webpackConfigFile?.let { project.file(it) }
                ?: project.buildDir.resolve("webpack.config.js")
    }

    @get:OutputDirectory
    val bundleDir by lazy { GenerateWebPackConfigTask.handleFile(project, project.frontendExtension.bundlesDirectory) }

    @get:InputFile
    val sourceFile by lazy { kotlinOutput(project) }

    @TaskAction
    fun buildBundle() {
        @Suppress("UNCHECKED_CAST")
        val webpackVersion = npm.nodeModulesDir.resolve("webpack/package.json")
                .let { JsonSlurper().parse(it) as Map<String, Any?> }["version"]
                ?.let { it as String }

        val nodePath = nodePath(project.rootProject, "node").first().absolutePath
        val webpackPath = npm.nodeModulesDir.resolve("webpack/bin/webpack.js").absolutePath
        val configFile = webPackConfigFile.absolutePath
        val processBuilderCommands = arrayListOf(
                nodePath,
                webpackPath,
                "--config", configFile
        )
        val webpackMajorVersion = webpackVersion
                ?.split('.')
                ?.firstOrNull()
                ?.toInt()
        if (webpackMajorVersion != null && webpackMajorVersion >= 4) {
            processBuilderCommands.addAll(arrayOf("--mode", config.mode))
        }
        ProcessBuilder(processBuilderCommands)
                .directory(project.rootProject.buildDir)
                .startWithRedirectOnFail(project.rootProject, processBuilderCommands.joinToString(" "))
    }
}
