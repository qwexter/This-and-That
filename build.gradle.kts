plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.detekt)
}

group = "xyz.qwexter"
version = "0.0.1"

kotlin {
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")

    val nativeTarget = when {
        hostOs == "Linux" && hostArch == "aarch64" -> linuxArm64("native")
        hostOs == "Linux" -> linuxX64("native")
        hostOs.startsWith("Mac") && hostArch == "aarch64" -> macosArm64("native")
        hostOs.startsWith("Mac") -> macosX64("native")
        hostOs.startsWith("Windows") -> mingwX64("native")
        else -> error("Unsupported host: $hostOs / $hostArch")
    }

    nativeTarget.binaries {
        executable {
            entryPoint = "xyz.qwexter.main"
        }
        all {
            when {
                hostOs == "Linux" -> linkerOpts("-L/usr/lib", "-lsqlite3", "--allow-shlib-undefined")
                hostOs.startsWith("Mac") -> linkerOpts("-lsqlite3")
                hostOs.startsWith("Windows") -> linkerOpts("-L${rootProject.projectDir}/libs/windows", "-lsqlite3")
            }
        }
    }

    sourceSets {
        nativeTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.server.test.host)
            implementation(libs.ktor.client.content.negotiation)
        }

        commonMain.dependencies {
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.default.headers)
            implementation(libs.ktor.server.resources)
            implementation(libs.ktor.server.serialization.kotlinx.json)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.native.driver)
        }
    }
}

detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
    )
}

dependencies {
    detektPlugins(libs.detekt.formatting)
    detektPlugins(libs.detekt.rules.coroutines)
}

sqldelight {

    databases {
        register("TatDatabase") {
            packageName.set("xyz.qwexter.db")
        }
        linkSqlite = false
    }
}
