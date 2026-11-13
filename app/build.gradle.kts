import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Assinatura de release: usa release.properties + release.keystore quando existirem
// (na raiz do projeto ou dentro de app/). Sem eles, o release cai para a chave de debug.
fun findProjectFile(name: String): File? =
    listOf(rootProject.file(name), file(name)).firstOrNull { it.exists() }

val releaseKeystoreFile = findProjectFile("release.keystore")
val releasePropsFile = findProjectFile("release.properties")
val releaseProps = Properties().apply {
    releasePropsFile?.inputStream()?.use { load(it) }
}
val hasReleaseSigning = releaseKeystoreFile != null && releasePropsFile != null

fun releaseProp(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { releaseProps.getProperty(it) }

android {
    namespace = "com.natam.cs50mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.natam.cs50mobile"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseProp("storePassword", "KEYSTORE_PASSWORD", "store.password")
                keyAlias = releaseProp("keyAlias", "KEY_ALIAS", "key.alias")
                keyPassword = releaseProp("keyPassword", "KEY_PASSWORD", "key.password")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Aparelho-alvo (Galaxy A31) é arm64: só essa ABI. Os .so do PRoot são minúsculos.
    // (Não usar ndk.abiFilters junto com splits.abi: o AGP recusa a combinação.)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        // Os executáveis do PRoot (libproot.so / libproot-loader.so) precisam ser extraídos
        // para nativeLibraryDir para poderem ser executados (execve não roda de dentro do APK).
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xbackend-threads=4")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
}
