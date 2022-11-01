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
    implementation("com.github.turasa", "signal-service-java", "2.15.3_unofficial_63")
    implementation("com.fasterxml.jackson.core", "jackson-databind", "2.13.4.2")
    implementation("com.google.protobuf", "protobuf-javalite", "3.21.6")
    implementation("org.bouncycastle", "bcprov-jdk15on", "1.70")
    implementation("org.slf4j", "slf4j-api", "2.0.3")
    implementation("org.xerial", "sqlite-jdbc", "3.39.3.0")
    implementation("com.zaxxer", "HikariCP", "5.0.1")

    testImplementation("org.junit.jupiter", "junit-jupiter", "5.9.0")
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
