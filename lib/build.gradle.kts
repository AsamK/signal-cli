plugins {
    `java-library`
    `check-lib-versions`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val libsignalClientPath = project.findProperty("libsignal_client_path")?.toString()

dependencies {
    if (libsignalClientPath == null) {
        implementation(libs.signalservice)
    } else {
        implementation(libs.signalservice) {
            exclude(group = "org.signal", module = "libsignal-client")
        }
        implementation(files(libsignalClientPath))
    }
    implementation(libs.jackson.databind)
    implementation(libs.bouncycastle)
    implementation(libs.slf4j.api)
    implementation(libs.sqlite)
    implementation(libs.hikari)

    testImplementation(libs.junit.jupiter)
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
