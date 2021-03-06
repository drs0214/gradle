package org.gradle.gradlebuild.test.integrationtests

import accessors.groovy
import accessors.java
import library
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.IdeaPlugin


enum class TestType(val prefix: String, val executers: List<String>) {
    INTEGRATION("integ", listOf("embedded", "forking", "noDaemon", "parallel", "instant", "watchFs")),
    CROSSVERSION("crossVersion", listOf("embedded", "forking"))
}


fun Project.addDependenciesAndConfigurations(prefix: String) {
    configurations {
        getByName("${prefix}TestImplementation") { extendsFrom(configurations["testImplementation"]) }

        val distributionRuntimeOnly = bucket("${prefix}TestDistributionRuntimeOnly", "Declare the distribution that is required to run tests")
        val localRepository = bucket("${prefix}TestLocalRepository", "Declare a local repository required as input data for the tests (e.g. :toolingApi)")
        val normalizedDistribution = bucket("${prefix}TestNormalizedDistribution", "Declare a normalized distribution (bin distribution without timestamp in version) to be used in tests")
        val binDistribution = bucket("${prefix}TestBinDistribution", "Declare a bin distribution to be used by tests - useful for testing the final distribution that is published")
        val allDistribution = bucket("${prefix}TestAllDistribution", "Declare a all distribution to be used by tests - useful for testing the final distribution that is published")
        val docsDistribution = bucket("${prefix}TestDocsDistribution", "Declare a docs distribution to be used by tests - useful for testing the final distribution that is published")
        val srcDistribution = bucket("${prefix}TestSrcDistribution", "Declare a src distribution to be used by tests - useful for testing the final distribution that is published")

        getByName("${prefix}TestRuntimeClasspath") { extendsFrom(distributionRuntimeOnly) }
        resolver("${prefix}TestDistributionRuntimeClasspath", "gradle-bin-installation", distributionRuntimeOnly)
        resolver("${prefix}TestFullDistributionRuntimeClasspath", "gradle-bin-installation")
        resolver("${prefix}TestLocalRepositoryPath", "gradle-local-repository", localRepository)
        resolver("${prefix}TestNormalizedDistributionPath", "gradle-normalized-distribution-zip", normalizedDistribution)
        resolver("${prefix}TestBinDistributionPath", "gradle-bin-distribution-zip", binDistribution)
        resolver("${prefix}TestAllDistributionPath", "gradle-all-distribution-zip", allDistribution)
        resolver("${prefix}TestDocsDistributionPath", "gradle-docs-distribution-zip", docsDistribution)
        resolver("${prefix}TestSrcDistributionPath", "gradle-src-distribution-zip", srcDistribution)
    }

    dependencies {
        "${prefix}TestRuntimeOnly"(library("junit5_vintage"))
        "${prefix}TestImplementation"(project(":internalIntegTesting"))
        "${prefix}TestFullDistributionRuntimeClasspath"(project(":distributionsFull"))
    }
}


internal
fun Project.addSourceSet(testType: TestType): SourceSet {
    val prefix = testType.prefix
    val main by java.sourceSets.getting
    return java.sourceSets.create("${prefix}Test") {
        compileClasspath += main.output
        runtimeClasspath += main.output
    }
}


internal
fun Project.createTasks(sourceSet: SourceSet, testType: TestType) {
    val prefix = testType.prefix
    val defaultExecuter = "embedded"

    // For all of the other executers, add an executer specific task
    testType.executers.forEach { executer ->
        val taskName = "$executer${prefix.capitalize()}Test"
        val testTask = createTestTask(taskName, executer, sourceSet, testType, Action {
            if (testType == TestType.CROSSVERSION) {
                // the main crossVersion test tasks always only check the latest version,
                // for true multi-version testing, we set up a test task per Gradle version,
                // (see CrossVersionTestsPlugin).
                systemProperties["org.gradle.integtest.versions"] = "default"
            }
        })
        if (executer == defaultExecuter) {
            // The test task with the default executer runs with 'check'
            tasks.named("check").configure { dependsOn(testTask) }
        }
    }
    // Create a variant of the test suite to force realization of component metadata
    if (testType == TestType.INTEGRATION) {
        createTestTask(prefix + "ForceRealizeTest", defaultExecuter, sourceSet, testType, Action {
            systemProperties["org.gradle.integtest.force.realize.metadata"] = "true"
        })
    }
}


internal
fun Project.createTestTask(name: String, executer: String, sourceSet: SourceSet, testType: TestType, extraConfig: Action<IntegrationTest>): TaskProvider<IntegrationTest> =
    tasks.register(name, IntegrationTest::class) {
        project.bucketProvider().configureTest(this, sourceSet, testType)
        description = "Runs ${testType.prefix} with $executer executer"
        systemProperties["org.gradle.integtest.executer"] = executer
        addDebugProperties()
        testClassesDirs = sourceSet.output.classesDirs
        classpath = sourceSet.runtimeClasspath
        extraConfig.execute(this)
    }


fun Project.integrationTestUsesSampleDir(vararg sampleDirs: String) {
    tasks.withType<IntegrationTest>().configureEach {
        systemProperty("declaredSampleInputs", sampleDirs.joinToString(";"))
        inputs.files(rootProject.files(sampleDirs))
            .withPropertyName("autoTestedSamples")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}


private
fun IntegrationTest.addDebugProperties() {
    // TODO Move magic property out
    if (project.hasProperty("org.gradle.integtest.debug")) {
        systemProperties["org.gradle.integtest.debug"] = "true"
        testLogging.showStandardStreams = true
    }
    // TODO Move magic property out
    if (project.hasProperty("org.gradle.integtest.verbose")) {
        testLogging.showStandardStreams = true
    }
    // TODO Move magic property out
    if (project.hasProperty("org.gradle.integtest.launcher.debug")) {
        systemProperties["org.gradle.integtest.launcher.debug"] = "true"
    }
}


internal
fun Project.configureIde(testType: TestType) {
    val prefix = testType.prefix
    val sourceSet = java.sourceSets.getByName("${prefix}Test")

    // We apply lazy as we don't want to depend on the order
    plugins.withType<IdeaPlugin> {
        with(model) {
            module {
                testSourceDirs = testSourceDirs + sourceSet.java.srcDirs
                testSourceDirs = testSourceDirs + sourceSet.groovy.srcDirs
                testResourceDirs = testResourceDirs + sourceSet.resources.srcDirs
            }
        }
    }
}


fun Test.getIncludeCategories(): MutableSet<String> {
    if (options is JUnitOptions) {
        return (options as JUnitOptions).includeCategories
    } else {
        return (options as JUnitPlatformOptions).includeTags
    }
}


fun Test.includeCategories(vararg categories: String) {
    if (options is JUnitOptions) {
        (options as JUnitOptions).includeCategories(*categories)
    } else {
        (options as JUnitPlatformOptions).includeTags(*categories)
    }
}


fun Test.excludeCategories(vararg categories: String) {
    if (options is JUnitOptions) {
        (options as JUnitOptions).excludeCategories(*categories)
    } else {
        (options as JUnitPlatformOptions).excludeTags(*categories)
    }
}


private
fun Project.bucket(name: String, description: String) = configurations.create(name) {
    isVisible = false
    isCanBeResolved = false
    isCanBeConsumed = false
    this.description = description
}


private
fun Project.resolver(name: String, libraryElements: String, extends: Configuration? = null) = configurations.create(name) {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(libraryElements))
    }
    isCanBeResolved = true
    isCanBeConsumed = false
    isVisible = false
    if (extends != null) {
        extendsFrom(extends)
    }
}
