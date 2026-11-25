// Module Kotlin pur (aucune dépendance Android) : moteur de compte à rebours et
// planificateur de jalons. Testé en JVM contre le jeu de cas partagé.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Le jeu de cas partagé vit à la racine du dépôt (../shared-cases), commun à JUnit et XCTest.
    val sharedCases = rootProject.layout.projectDirectory.dir("../shared-cases")
    inputs.dir(sharedCases).withPropertyName("sharedCases")
    systemProperty("sharedCasesDir", sharedCases.asFile.absolutePath)
}
