plugins {
    id("org.openstreetmap.josm").version("0.8.2")
}

import org.gradle.api.tasks.JavaExec
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.options.Option
import org.openstreetmap.josm.gradle.plugin.task.github.PublishToGithubReleaseTask
import java.util.Properties

abstract class ReleaseTask : DefaultTask() {
    @Option(
        option = "release-label",
        description = "the release label, for example 0.2.2",
    )
    fun releaseLabel(label: String) {
        require(label.isNotBlank()) { "release-label must not be blank" }
        listOf("createGithubRelease", "publishToGithubRelease").forEach { taskName ->
            val task = project.tasks.named(taskName).get()
            task.javaClass.getMethod("setReleaseLabel", String::class.java).invoke(task, label)
        }
    }
}

version = "0.3.0"

val releaseJarName = "account_manager.jar"
val releaseJarPath = layout.buildDirectory.file("dist/$releaseJarName")

val localProxyProperties = Properties().apply {
    val configFile = rootProject.file("proxy.properties")
    if (configFile.isFile) {
        configFile.inputStream().use(::load)
    }
}

fun proxySetting(name: String, fallback: String = "") =
    providers.gradleProperty(name).orElse(localProxyProperties.getProperty(name, fallback))

val josmDevProxyEnabled = proxySetting("josmDevProxyEnabled", "false").map(String::toBoolean)
val josmDevProxyHost = proxySetting("josmDevProxyHost")
val josmDevProxyPort = proxySetting("josmDevProxyPort")

if (josmDevProxyEnabled.get()) {
    require(josmDevProxyHost.get().isNotBlank()) {
        "josmDevProxyHost must be set when the development proxy is enabled"
    }
    require(josmDevProxyPort.get().toIntOrNull() in 1..65535) {
        "josmDevProxyPort must be a number between 1 and 65535"
    }
}

val josmJvmArgs = mutableListOf(
    "--add-exports=java.base/sun.security.action=ALL-UNNAMED",
    "--add-exports=java.desktop/com.sun.imageio.plugins.jpeg=ALL-UNNAMED",
    "--add-exports=java.desktop/com.sun.imageio.spi=ALL-UNNAMED",
    "-Dfile.encoding=UTF-8",
    "-Dsun.stdout.encoding=UTF-8",
    "-Dsun.stderr.encoding=UTF-8",
)

if (josmDevProxyEnabled.get()) {
    josmJvmArgs += listOf(
        "-Dhttp.proxyHost=${josmDevProxyHost.get()}",
        "-Dhttp.proxyPort=${josmDevProxyPort.get()}",
        "-Dhttps.proxyHost=${josmDevProxyHost.get()}",
        "-Dhttps.proxyPort=${josmDevProxyPort.get()}",
    )
}

repositories {
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
    mavenCentral()
}

val profileTests by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free account profile unit tests"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.example.josm.accountmanager.AccountProfileTest")
}

tasks.named("check") {
    dependsOn(profileTests)
}

josm {
    pluginName = "account_manager"
    josmCompileVersion = "19555"
    if (josmDevProxyEnabled.get()) {
        initialPreferences.set(
            """
            <tag key="proxy.policy" value="use-http-proxy"/>
            <tag key="proxy.http.host" value="${josmDevProxyHost.get()}"/>
            <tag key="proxy.http.port" value="${josmDevProxyPort.get()}"/>
            """.trimIndent()
        )
    }
    manifest {
        description = "Manage and switch multiple account profiles for OSM-compatible platforms"
        mainClass = "com.example.josm.accountmanager.AccountManagerPlugin"
        minJosmVersion = "18991"
        author = "smallCat"
        iconPath = "images/account_manager.png"
    }
    github {
        repositoryOwner = "daishu0000"
        repositoryName = "josm-account-manager"
        targetCommitish = "main"
    }
}

tasks.named<JavaExec>("runJosm") {
    jvmArgs(josmJvmArgs)
}

tasks.named<JavaExec>("debugJosm") {
    jvmArgs(josmJvmArgs)
}

tasks.named<PublishToGithubReleaseTask>("publishToGithubRelease") {
    dependsOn("dist")
    mustRunAfter("createGithubRelease")
    localJarPath = releaseJarPath.get().asFile.absolutePath
    remoteJarName = releaseJarName
}

tasks.register<ReleaseTask>("release") {
    group = "release"
    description = "Build dist JAR, create GitHub release, and upload $releaseJarName"
    dependsOn("createGithubRelease", "publishToGithubRelease")
}
