package org.jetbrains.kotlin.gradle.frontend.npm

import org.gradle.api.Project
import org.gradle.api.tasks.InputFile
import org.jetbrains.kotlin.gradle.frontend.*
import org.jetbrains.kotlin.gradle.frontend.util.mkdirsOrFail
import java.io.File
import java.util.*

/**
 * @author Sergey Mashkov
 */
open class NpmExtension(val project: Project) {
    val dependencies: MutableList<Dependency> = ArrayList()

    val versionReplacements: MutableList<Dependency> = ArrayList()

    val developmentDependencies: MutableList<Dependency> = ArrayList()

    @InputFile
    var nodeModulesDir: File = project.file(project.rootProject.buildDir.absolutePath + File.separator + "node_modules").apply {
        mkdirsOrFail()
    }

    @JvmOverloads
    fun dependency(name: String, version: String = "*") {
        dependencies.add(Dependency(name, version, Dependency.RuntimeScope))
    }

    fun replaceVersion(name: String, version: String) {
        versionReplacements.add(Dependency(name, version, Dependency.RuntimeScope))
    }

    @JvmOverloads
    fun devDependency(name: String, version: String = "*") {
        developmentDependencies.add(Dependency(name, version, Dependency.DevelopmentScope))
    }
}