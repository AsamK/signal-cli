plugins {
    java
    application
    eclipse
    `check-lib-versions`
    id("org.graalvm.buildtools.native") version "0.9.14"
}

version = "0.11.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("org.asamk.signal.Main")
}

graalvmNative {
    binaries {
        this["main"].run {
            configurationFileDirectories.from(file("graalvm-config-dir"))
            buildArgs.add("--report-unsupported-elements-at-runtime")
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.bouncycastle", "bcprov-jdk15on", "1.70")
    implementation("com.fasterxml.jackson.core", "jackson-databind", "2.13.4")
    implementation("net.sourceforge.argparse4j", "argparse4j", "0.9.0")
    implementation("com.github.hypfvieh", "dbus-java-transport-native-unixsocket", "4.2.1")
    implementation("org.slf4j", "slf4j-api", "2.0.3")
    implementation("ch.qos.logback", "logback-classic", "1.4.3")
    implementation("org.slf4j", "jul-to-slf4j", "2.0.3")
    implementation(project(":lib"))
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

tasks.withType<Jar> {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Main-Class" to application.mainClass.get()
        )
    }
}

task("fatJar", type = Jar::class) {
    archiveBaseName.set("${project.name}-fat")
    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/NOTICE*",
        "META-INF/LICENSE*",
        "META-INF/INDEX.LIST",
        "**/module-info.class"
    )
    duplicatesStrategy = DuplicatesStrategy.WARN
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}
