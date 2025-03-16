plugins {
    androidApplication
    androidLibrary
    kaptPlugin
    daggerHilt
    navigationSafeArgsKotlin
    kotlinParcelize
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(17)
}
android {
    compileSdk = Versions.compilesdk
    namespace = "ng.mint.ocrscanner"
    defaultConfig {
        applicationId = Application.id
        minSdk = Versions.minsdk
        targetSdk = Versions.targetsdk
        versionCode = Application.versionCode
        versionName = Application.versionName
        testInstrumentationRunner = Application.testInstrumentationRunner
    }

    buildFeatures {
        buildConfig = true
        dataBinding = true
        viewBinding = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        
        getByName("debug") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }

    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/license.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/AL2.0")
            excludes.add("META-INF/LGPL2.1")
            excludes.add("META-INF/*.kotlin_module")
            excludes.add("META-INF/versions/9/previous-compilation-data.bin")
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        animationsDisabled = true
    }
    
    lint {
        abortOnError = false
        checkReleaseBuilds = true
        checkDependencies = true
        disable += listOf(
            "InvalidPackage",
            "ObsoleteSdkInt",
            "GradleDependency"
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }
}

// Configure KAPT settings - not needed for Moshi anymore since we use KSP
kapt {
    correctErrorTypes = true
    useBuildCache = true
    arguments {
        // Room specific arguments
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
        
        // Dagger/Hilt specific arguments
        arg("dagger.hilt.disableModulesHaveInstallInCheck", "true")
        arg("dagger.fastInit", "enabled")
        arg("dagger.hilt.android.internal.projectType", "app")
        arg("dagger.hilt.internal.useAggregatingRootProcessor", "true")
    }
    
    javacOptions {
        option("-Xmaxerrs", 500)
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("io.card:android-sdk:5.5.1")
    implementAll(Dependencies.implementations)
    implementation("com.github.kirich1409:viewbindingpropertydelegate-noreflection:1.5.9")
    implementAll(SupportDependencies.supportImplementation)
    implementAll(AnnotationProcessors.RegularImplementation) // Add SQLite JDBC here
    
    testImplementAll(TestDependencies.testImplementation)
    testImplementation("org.hamcrest:hamcrest:2.2")
    testAndroidImplementAll(AndroidTestDependencies.androidTestImplementation)
    // Use only KSP for Moshi code generation
    ksp("com.squareup.moshi:moshi-kotlin-codegen:${Versions.moshiKotlin}")
    // KAPT is still needed for Room, Hilt, and other annotation processors
    kaptImplementAll(AnnotationProcessors.AnnotationProcessorsImplementation)
    kaptAndroidTestImplementAll(AnnotationProcessors.AnnotationProcessorsImplementation)
    debugImplementationAll(DebugDependencies.debugImplementation)
}