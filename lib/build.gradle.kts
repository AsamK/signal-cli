plugins {
    `java-library`
    `check-lib-versions`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.github.turasa:signal-service-java:2.15.3_unofficial_33")
    api("com.fasterxml.jackson.core", "jackson-databind", "2.13.0")
    implementation("com.google.protobuf:protobuf-javalite:3.11.4")
    implementation("org.bouncycastle:bcprov-jdk15on:1.69")
    implementation("org.slf4j:slf4j-api:1.7.32")
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
