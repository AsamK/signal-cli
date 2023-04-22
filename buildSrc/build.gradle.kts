plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("17"))
    }
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("check-lib-versions") {
            id = "check-lib-versions"
            implementationClass = "CheckLibVersionsPlugin"
        }
    }
}
