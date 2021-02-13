plugins {
    java
    application
    eclipse
    `maven-publish`
    `check-lib-versions`
    `java-library-distribution`
}

val projectVersion: String by project
val mavenGroup: String by project

version = projectVersion
group = mavenGroup

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application {
    mainClass.set("org.asamk.signal.Main")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk15on:1.68")
    implementation("net.sourceforge.argparse4j:argparse4j:0.8.1")
    implementation("com.github.hypfvieh:dbus-java:3.2.4")
    implementation("org.slf4j:slf4j-simple:1.7.30")
    implementation(project(":lib"))
}

configurations {
    implementation {
        resolutionStrategy.failOnVersionConflict()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

distributions {
    main {
        distributionBaseName.set("signal-cli")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Jar> {
    manifest {
        attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Main-Class" to application.mainClass.get(),
                "Automatic-Module-Name" to project.name.replace('-', '.'),
                // Custom (non-standard) attribute
                "Maven-Group" to project.group
        )
    }
    dependsOn("generatePomFileForMavenPublication")
    into("META-INF/maven/${project.group}/${project.name}") {
        from("$buildDir/publications/maven/pom-default.xml")
        rename(".*", "pom.xml")
    }
}

tasks.withType<JavaExec> {
    val appArgs: String? by project
    if (appArgs != null) {
        // allow passing command-line arguments to the main application e.g.:
        // $ gradle run -PappArgs="['-u', '+...', 'daemon', '--json']"
        args = groovy.util.Eval.me(appArgs) as MutableList<String>
    }
}

val assembleNativeImage by tasks.registering {
    dependsOn("assemble")

    var graalVMHome = ""
    doFirst {
        graalVMHome = System.getenv("GRAALVM_HOME")
                ?: throw GradleException("Required GRAALVM_HOME environment variable not set.")
    }

    doLast {
        val nativeBinaryOutputPath = "$buildDir/native-image"
        val nativeBinaryName = "signal-cli"

        mkdir(nativeBinaryOutputPath)

        exec {
            workingDir = File(".")
            commandLine("$graalVMHome/bin/native-image",
                    "-H:Path=$nativeBinaryOutputPath",
                    "-H:Name=$nativeBinaryName",
                    "-H:JNIConfigurationFiles=graalvm-config-dir/jni-config.json",
                    "-H:DynamicProxyConfigurationFiles=graalvm-config-dir/proxy-config.json",
                    "-H:ResourceConfigurationFiles=graalvm-config-dir/resource-config.json",
                    "-H:ReflectionConfigurationFiles=graalvm-config-dir/reflect-config.json",
                    "--no-fallback",
                    "--allow-incomplete-classpath",
                    "--report-unsupported-elements-at-runtime",
                    "--enable-url-protocols=http,https",
                    "--enable-https",
                    "--enable-all-security-services",
                    "-cp",
                    sourceSets.main.get().runtimeClasspath.asPath,
                    application.mainClass.get())
        }
    }
}
