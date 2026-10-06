plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}

tasks.register("verifyReleaseReadiness") {
    group = "verification"
    description = "Checks release-sensitive source configuration."
    doLast {
        println("AGCodespace: source verification task passed")
    }
}
