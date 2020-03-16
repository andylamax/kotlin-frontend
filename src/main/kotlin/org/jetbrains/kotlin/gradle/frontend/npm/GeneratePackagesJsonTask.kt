package org.jetbrains.kotlin.gradle.frontend.npm

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.frontend.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import java.io.*
import java.util.*

/**
 * @author Sergey Mashkov
 */
open class GeneratePackagesJsonTask : DefaultTask() {
    @Internal
    lateinit var dependenciesProvider: () -> List<Dependency>

    @get:Internal
    val toolsDependencies: List<Dependency> by lazy { dependenciesProvider() }

    @get:Input
    @Suppress("unused")
    val toolsDependenciesInput: String
        get() = toolsDependencies.joinToString()

    @get:InputFiles
    val unpackResults: List<File>
        get() = project.tasks.filterIsInstance<UnpackGradleDependenciesTask>().map { it.resultFile }

    @InputDirectory
    @Optional
    val configPartsDir = project.projectDir.resolve("package.json.d").apply {
        mkdirsOrFail()
    }

    //    @get:Input
    private val npm = project.extensions.getByType(NpmExtension::class.java)

    @get:Input
    @Suppress("unused")
    val dependenciesInput: String
        get() = npm.dependencies.joinToString()

    @get:Input
    @Suppress("unused")
    val devDependenciesInput: String
        get() = npm.developmentDependencies.joinToString()

    @Suppress("unused")
    @get:Input
    val versionReplacementsInput: String
        get() = npm.versionReplacements.joinToString()

    @get:Input
    val moduleNames: List<String> by lazy {
        project.tasks.withType(KotlinJsCompile::class.java)
                .filter { !it.name.contains("test", ignoreCase = true) }
                .mapNotNull { it.kotlinOptions.outputFile?.substringAfterLast('/')?.substringAfterLast('\\')?.removeSuffix(".js") }
    }

    //    @OutputFile
    private val packageJsonFile
        get() = project.file("build/package.json").apply {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        }

    //    @OutputFile
    private val npmrcFile: File
        get() = packageJsonFile.resolveSibling(".npmrc").apply {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        }

    @OutputFile
    val buildPackageJsonFile: File?

    //    @OutputFile
    private val globalPackageJsonFile: File
        get() {
            val globalPackgJsonFile = project.rootProject.buildDir.resolve("package.json").apply {
                parentFile?.mkdirs()
                if (!exists()) createNewFile()
            }
            return if (globalPackgJsonFile.isSame(packageJsonFile)) {
                packageJsonFile
            } else globalPackgJsonFile
        }

    init {
        if (configPartsDir.exists()) {
            (inputs as TaskInputs).dir(configPartsDir)
        }
        val packgJson = project.convention.findPlugin(JavaPluginConvention::class.java)?.sourceSets?.let { sourceSets ->
            sourceSets.findByName("main")?.output?.resourcesDir?.resolve("package.json")
        }
        buildPackageJsonFile = if (packgJson?.isSame(packageJsonFile) == true) {
            packageJsonFile
        } else packgJson

        if (buildPackageJsonFile != null) {
            outputs.file(buildPackageJsonFile)
        }
        outputs.file(globalPackageJsonFile)

        onlyIf {
            npm.dependencies.isNotEmpty() || npm.developmentDependencies.isNotEmpty() || toolsDependencies.isNotEmpty()
        }
    }

    private fun File.isSame(f: File) = absolutePath == f.absolutePath

    @TaskAction
    fun generate() {
        logger.info("Configuring npm")

        val dependencies = npm.dependencies + (project.tasks.filterIsInstance<UnpackGradleDependenciesTask>().map { task ->
            task.resultNames?.map { Dependency(it.name, it.semver, Dependency.RuntimeScope) }
                    ?: task.resultFile.readLinesOrEmpty()
                            .map { it.split("/", limit = 4).map(String::trim) }
                            .filter { it.size == 4 }
                            .map { Dependency(it[0], it[2], Dependency.RuntimeScope) }
        }).flatten() + toolsDependencies.filter { it.scope == Dependency.RuntimeScope }

        val devDependencies = mutableListOf(*npm.developmentDependencies.toTypedArray())

        devDependencies.addAll(toolsDependencies.filter {
            (it.scope == Dependency.DevelopmentScope) && devDependencies.all { dep -> dep.name != it.name }
        })

        if (logger.isDebugEnabled) {
            logger.debug(dependencies.joinToString(prefix = "Dependencies:\n", separator = "\n") { "${it.name}: ${it.versionOrUri}" })
        }

        val packagesJson: Map<*, *> = mapOf(
                "name" to (moduleNames.singleOrNull() ?: project.name ?: "noname"),
                "version" to (toSemver(project.version.toString())),
                "description" to "simple description",
                "main" to (moduleNames.singleOrNull()),
                "dependencies" to dependencies.associateBy({ it.name }, { it.versionOrUri }),
                "devDependencies" to devDependencies.associateBy({ it.name }, { it.versionOrUri })
        )

        val number = "^\\d+".toRegex()
        val allIncluded = configPartsDir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.canRead() }
                .sortedBy { number.find(it.nameWithoutExtension)?.value?.toInt() ?: 0 }
                .map { LinkedHashMap(JsonSlurper().parse(it) as Map<*, *>) }

        val resultJson = allIncluded.fold(packagesJson, ::mergeMaps)
        listOf(packageJsonFile, buildPackageJsonFile, globalPackageJsonFile).mapNotNull {
            it?.absolutePath
        }.toSet().forEach {
            File(it).writeText(JsonBuilder(resultJson).toPrettyString())
        }
        npmrcFile.writeText("""
        progress=false
        package-lock=false
        # cache-min=3600
        """.trimIndent())

        npmrcFile.resolveSibling("package-lock.json").delete()
    }
}
