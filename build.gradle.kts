plugins {
    id("org.openstreetmap.josm").version("0.8.2")
}

import org.gradle.api.tasks.JavaExec

version = "0.3.0"

val josmDevProxyEnabled = providers.gradleProperty("josmDevProxyEnabled")
    .map(String::toBoolean).orElse(true)
val josmDevProxyHost = providers.gradleProperty("josmDevProxyHost").orElse("127.0.0.1")
val josmDevProxyPort = providers.gradleProperty("josmDevProxyPort").orElse("7890")

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
        description = "Manage and switch multiple OAuth token profiles for OSM-compatible platforms"
        mainClass = "com.example.josm.accountmanager.AccountManagerPlugin"
        minJosmVersion = "19555"
        author = "account_manager contributors"
    }
}

tasks.named<JavaExec>("runJosm") {
    jvmArgs(josmJvmArgs)
}

tasks.named<JavaExec>("debugJosm") {
    jvmArgs(josmJvmArgs)
}
