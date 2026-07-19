plugins {
    id("org.openstreetmap.josm").version("0.8.2")
}

import org.gradle.api.tasks.JavaExec

version = "0.1.0"

val josmJvmArgs = listOf(
    "--add-exports=java.base/sun.security.action=ALL-UNNAMED",
    "--add-exports=java.desktop/com.sun.imageio.plugins.jpeg=ALL-UNNAMED",
    "--add-exports=java.desktop/com.sun.imageio.spi=ALL-UNNAMED",
    "-Dfile.encoding=UTF-8",
    "-Dsun.stdout.encoding=UTF-8",
    "-Dsun.stderr.encoding=UTF-8",
    "-Dhttp.proxyHost=127.0.0.1",
    "-Dhttp.proxyPort=7890",
    "-Dhttps.proxyHost=127.0.0.1",
    "-Dhttps.proxyPort=7890",
)

repositories {
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
    mavenCentral()
}

josm {
    pluginName = "account_manager"
    josmCompileVersion = "19555"
    initialPreferences.set(
        """
        <tag key="proxy.policy" value="use-http-proxy"/>
        <tag key="proxy.http.host" value="127.0.0.1"/>
        <tag key="proxy.http.port" value="7890"/>
        """.trimIndent()
    )

    manifest {
        description = "A minimal Account Manager plugin for JOSM"
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
