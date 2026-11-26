plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.goalwidgets"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.goalwidgets"
        minSdk = 26 // Android 8.0
        targetSdk = 35
        // Surchargés par la CI (-PversionCode=1000+run, -PversionName="1.<run> (<sha>)") ;
        // en local : valeurs historiques (versionCode 1 = build de debug installé à la main).
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"
        // Mises à jour automatiques depuis les GitHub Releases (désactivées pour les builds « preview »).
        buildConfigField("boolean", "AUTO_UPDATE", "true")
        // Seul le français est traduit : les traductions des bibliothèques (Material, Compose…) dans
        // d'autres langues ne font que grossir l'APK. Ajouter ici toute nouvelle langue (values-xx/).
        resourceConfigurations += listOf("fr")
    }

    // Signature de la release publiée par la CI (.github/workflows/android-release.yml) : la clé
    // de debug de l'appareil, pour que la mise à jour s'installe par-dessus le build de debug
    // sans perte de données. Sans ces variables (build local), la release reste non signée.
    val releaseKeystore = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // R8 : code et ressources inutilisés retirés, APK bien plus léger (règles : proguard-rules.pro).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
        // Build de test d'une branche (.github/workflows/android-preview.yml) : identique à la release
        // (minifiée) mais installée À CÔTÉ de l'app (autre applicationId, autre nom), sans mise à jour auto.
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "AUTO_UPDATE", "false")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }
    sourceSets {
        // Même code « sans debug » que la release (NotificationDebugSection vide).
        getByName("preview") { java.srcDir("src/release/kotlin") }
    }
    // Bloc de métadonnées des dépendances (réservé au Play Store) : inutile hors Play Store.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources {
            // Fichiers de build des bibliothèques, jamais lus à l'exécution.
            excludes += listOf("DebugProbesKt.bin", "META-INF/*.version", "META-INF/androidx.*", "META-INF/com.google.*")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG : section « Debug » des notifications (t14).
        buildConfig = true
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        // Captures des widgets (WidgetScreenshots, Robolectric) : ressources Android des bibliothèques.
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    // Schémas Room versionnés (app/schemas) : base des futures migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)

    // Tests JVM de la migration Room (SQLite réel via JDBC, sans appareil ni Robolectric).
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.kotlinx.serialization.json)
    // Captures des widgets en JVM (Robolectric, rendu natif) : JUnit 4 via le moteur « vintage ».
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.withType<Test>().configureEach {
    // -Pscreenshots : WidgetScreenshots écrit les PNG dans build/widget-screenshots (sinon ignoré).
    systemProperty("widgetScreenshotsDir", layout.buildDirectory.dir("widget-screenshots").get().asFile.absolutePath)
    if (providers.gradleProperty("screenshots").isPresent) {
        systemProperty("widgetScreenshots", "true")
    } else {
        // Robolectric démarre un bac à sable lourd : hors -Pscreenshots, la classe n'est pas lancée.
        filter.excludeTestsMatching("*WidgetScreenshots")
    }
    // Schémas Room exportés (app/schemas) lus par Migration1To2Test.
    val schemas = layout.projectDirectory.dir("schemas")
    inputs.dir(schemas).withPropertyName("roomSchemas")
    systemProperty("roomSchemasDir", schemas.asFile.absolutePath)
}
