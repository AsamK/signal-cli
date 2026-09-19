plugins {
    `java-library`
    `check-lib-versions`
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25

    if (!JavaVersion.current().isCompatibleWith(targetCompatibility)) {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(targetCompatibility.majorVersion))
        }
    }
}

val libsignalClientPath = project.findProperty("libsignal_client_path")?.toString()
val libsignalClientVersion = "0.101.0"
val androidClassifier = project.findProperty("androidClassifier")?.toString()

dependencies {
    implementation(libs.signalnetwork) {
        exclude(group = "org.signal", module = "libsignal-client")
    }

    if (libsignalClientPath == null) {
        implementation("org.signal:libsignal-client:$libsignalClientVersion")
        if (androidClassifier != null) {
            implementation(
                group = "org.signal",
                name = "libsignal-client",
                version = libsignalClientVersion,
                classifier = androidClassifier,
            )
        }
    } else {
        implementation(files(libsignalClientPath))
    }
    implementation(libs.jackson.databind)
    implementation(libs.bouncycastle)
    implementation(libs.slf4j.api)
    implementation(libs.sqlite)
    if (androidClassifier != null) {
        runtimeOnly(variantOf(libs.sqlite) {
            classifier("natives-android")
        })
    }
    implementation(libs.hikari)
    compileOnly(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(platform(libs.junit.jupiter.bom))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

configurations {
    implementation {
        resolutionStrategy.failOnVersionConflict()
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "org.asamk.signal.manager")
    }
}
