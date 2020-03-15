package org.jetbrains.kotlin.gradle.frontend

import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.ProcessLauncher
import org.gradle.api.Project
import org.gradle.api.internal.project.DefaultProject
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import sun.java2d.loops.ProcessPath
import java.io.File
import java.net.URL
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class NewMppSubProjectTest {
    private val gradleVersion: String = "6.1.1"
    private val kotlinVersion: String = "1.3.70"

    private val port = 8098
    private val builder = BuildScriptBuilder()

    private lateinit var settingsGradleFile: File
    private lateinit var buildGradleFile: File
    private lateinit var runner: GradleRunner

    @get:Rule
    val testName = TestName()

    @get:Rule
    val failedRule = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description?) {
            val dst = File("build/tests/${testName.methodName.replace("[", "-").replace("]", "")}").apply { mkdirsOrFail() }
            projectDir.root.copyRecursively(dst, true) { file, copyError ->
                System.err.println("Failed to copy $file due to ${copyError.message}")
                OnErrorAction.SKIP
            }
            println("Copied project to ${dst.absolutePath}")
        }

        /*
        // useful for debugging
        override fun succeeded(description: Description?) {
            failed(null, description)
        }
        // */
    }

    @get:Rule
    val projectDir = TemporaryFolder()

    val D = "$"

    fun File.makeParentsAndWriteText(text: String) {
        parentFile.mkdirsOrFail()
        writeText(text)
    }

    @Before
    fun setUp() {
        projectDir.create()
        projectDir.root.resolve("build/kotlin-build/caches").mkdirsOrFail()

        buildGradleFile = projectDir.root.resolve("sub/build.gradle")
        projectDir.root.resolve("build.gradle").makeParentsAndWriteText("""
            ext {
                kotlin_version = "$kotlinVersion"
            }
        """.trimIndent())
        settingsGradleFile = projectDir.root.resolve("settings.gradle")

        projectDir.root.resolve("sub/src/commonMain/kotlin/sample/Sample.kt").makeParentsAndWriteText("expect fun f(): Int")
        projectDir.root.resolve("sub/src/commonTest/kotlin/sample/SampleTests.kt").makeParentsAndWriteText("""
import kotlin.test.*

class SampleTests {
    @Test
    fun testMe() {
        assertTrue(f() > 0)
    }
}
                """
        )

        projectDir.root.resolve("sub/src/jsMain/kotlin/sample/SampleJs.kt").makeParentsAndWriteText(
                """
                fun main() {
                    println("This works")        
                }
                
                actual fun f(): Int = 1
                """.trimIndent())
        projectDir.root.resolve("sub/src/jsMain/resources/index.html").makeParentsAndWriteText(
                """
                    <html>
                        <head><title>Mpp: Sub Test</title></head>
                        <body><h1>Works</h2><body>
                        <script src='main.bundle.js'>
                    </html>
                """.trimIndent()
        )
        projectDir.root.resolve("sub/src/jsTest/kotlin/sample/SampleTestsJs.kt").makeParentsAndWriteText(
                """
import kotlin.test.*

class SampleTestsJs {
    @Test
    fun testMe() {
        assertTrue(f() == 1)
    }
}
                """)

        projectDir.root.resolve("sub/src/jvmMain/kotlin/sample/SampleJvm.kt").makeParentsAndWriteText("actual fun f(): Int = 2")
        projectDir.root.resolve("sub/src/jvmTest/kotlin/sample/SampleTestsJvm.kt").makeParentsAndWriteText(
                """
import kotlin.test.*

class SampleTestsJvm {
    @Test
    fun testMe() {
        assertTrue(f() == 2)
    }
}
                """
        )

        buildGradleFile.parentFile.mkdirsOrFail()
        settingsGradleFile.parentFile.mkdirsOrFail()

        runner = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withPluginClasspath()
                .withGradleVersion(gradleVersion)

        settingsGradleFile.writeText(
                """
pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "kotlin-multiplatform") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:$D{requested.version}")
            }
        }
    }
    
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "kotlin-frontend") {
                useModule('tz.co.asoft:kotlin-frontend:0.0.46')
            }
        }
    }

    repositories {
        mavenCentral()
        maven { url 'https://plugins.gradle.org/m2/' }
    }
}

rootProject.name = 'new-mpp'
include(":sub")
                """
        )
    }

    fun ProcessBuilder.startProcess(name: String, exec: Executor = DummyExecutor): Process {
        require(command().isNotEmpty()) { "No command specified" }

        val process = Native.get(ProcessLauncher::class.java).let { l ->
            l.start(this)!!
        }

        val buffered = OutputStreamWithBuffer(System.out, 8192)

        val rc = try {
            ProcessHandler(process, buffered, buffered, exec).startAndWaitFor()
        } catch (t: Throwable) {
            println("Process ${command().first()} failed: ${t.message}")
            process.destroyForcibly()
            -1
        }

        if (rc != 0) {
            println(buffered.lines().toString(Charsets.UTF_8))
            println("Command failed (exit code = $rc): ${command().joinToString(" ")}")
            throw Throwable("$name failed (exit code = $rc)")
        }

        return process
    }

    @Test
    fun simpleMppSubProject() {
        buildGradleFile.writeText(
                """
plugins {
    id 'kotlin-multiplatform' version '$kotlinVersion'
    id 'kotlin-frontend'
}

apply plugin: "kotlin-dce-js"

repositories {
    jcenter()
    mavenCentral()
}

kotlin {
    js {
        compilations.all {
            tasks[compileKotlinTaskName].kotlinOptions {
                metaInfo = true
                sourceMap = true
                moduleKind = 'commonjs'
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation 'org.jetbrains.kotlin:kotlin-stdlib-common'
            }
        }
        commonTest {
            dependencies {
                implementation 'org.jetbrains.kotlin:kotlin-test-common'
                implementation 'org.jetbrains.kotlin:kotlin-test-annotations-common'
            }
        }
        jsMain {
            dependencies {
                implementation 'org.jetbrains.kotlin:kotlin-stdlib-js'
            }
        }
        jsTest {
            dependencies {
                implementation 'org.jetbrains.kotlin:kotlin-test-js'
            }
        }
    }
}

kotlinFrontend {
    npm {
//        dependency("is-number","7.0.0")
//        devDependency("karma")
    }

    sourceMaps = false

    webpackBundle {
        bundleName = "main"
    }
}
                """
        )

        println("Running npm-install")
        runner.withArguments(":sub:npm-install").build().apply {
            val nodeModules = projectDir.root.resolve("build/node_modules").listFiles()
            nodeModules?.take(3)?.forEach { println(it.name) }
            assertEquals(true, nodeModules?.isNotEmpty())
            println(output)
            assertEquals(TaskOutcome.SUCCESS, task(":sub:npm-install")?.outcome)
        }

        println("Running Bundle")
        runner.withArguments(":sub:bundle").build().apply {
            assertEquals(TaskOutcome.UP_TO_DATE, task(":sub:npm-preunpack")?.outcome)
            assertEquals(TaskOutcome.UP_TO_DATE, task(":sub:npm-install")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, task(":sub:webpack-config")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, task(":sub:webpack-bundle")?.outcome)
            assertTrue { projectDir.root.resolve("sub/build/classes/kotlin/js/main/sub.js").isFile }
            assertTrue { projectDir.root.resolve("sub/build/bundle/main.bundle.js").isFile }
            println(output)
        }

        println("Running run")
        runner.withArguments(":sub:run").build().apply {
            assertEquals(TaskOutcome.SUCCESS, task(":sub:run")?.outcome)
            println(output)
        }

        println("Running stop")
        runner.withArguments("stop").build().apply {
            assertEquals(TaskOutcome.SUCCESS, task(":sub:stop")?.outcome)
            println(output)
        }
    }
}