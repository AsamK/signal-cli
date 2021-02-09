plugins {
    `java-library`
    `check-lib-versions`
}

val projectVersion: String by project
val mavenGroup: String by project

version = projectVersion
group = mavenGroup

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    api("com.github.turasa:signal-service-java:2.15.3_unofficial_19")
    implementation("com.google.protobuf:protobuf-javalite:3.10.0")
    implementation("org.bouncycastle:bcprov-jdk15on:1.68")
    implementation("org.slf4j:slf4j-api:1.7.30")
}

configurations {
    implementation {
        resolutionStrategy.failOnVersionConflict()
    }
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            // use a more meaningful name than 'lib'
            "Automatic-Module-Name" to "signal-lib",
            // Custom (non-standard) attribute
            "Maven-Group" to project.group
        )
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
