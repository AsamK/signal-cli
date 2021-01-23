plugins {
    java
    application
    eclipse
}

version = "0.7.4"

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

tasks.withType<JavaExec> {
    val appArgs: String? by project
    if (appArgs != null) {
        // allow passing command-line arguments to the main application e.g.:
        // $ gradle run -PappArgs="['-u', '+...', 'daemon', '--json']"
        args = groovy.util.Eval.me(appArgs) as MutableList<String>
    }
}

// Find any 3rd party libraries which have released new versions
// to the central Maven repo since we last upgraded.
val checkLibVersions by tasks.registering {
    doLast {
        val checked = kotlin.collections.HashSet<Dependency>()
        allprojects {
            configurations.forEach { configuration ->
                configuration.allDependencies.forEach { dependency ->
                    val version = dependency.version
                    if (!checked.contains(dependency)) {
                        val group = dependency.group
                        val path = group?.replace(".", "/") ?: ""
                        val name = dependency.name
                        val metaDataUrl = "https://repo1.maven.org/maven2/$path/$name/maven-metadata.xml"
                        try {
                            val url = org.codehaus.groovy.runtime.ResourceGroovyMethods.toURL(metaDataUrl)
                            val metaDataText = org.codehaus.groovy.runtime.ResourceGroovyMethods.getText(url)
                            val metadata = groovy.util.XmlSlurper().parseText(metaDataText)
                            val newest = (metadata.getProperty("versioning") as groovy.util.slurpersupport.GPathResult).getProperty("latest")
                            if (version != newest.toString()) {
                                println("UPGRADE {\"group\": \"$group\", \"name\": \"$name\", \"current\": \"$version\", \"latest\": \"$newest\"}")
                            }
                        } catch (e: Throwable) {
                            logger.debug("Unable to download or parse $metaDataUrl: $e.message")
                        }
                        checked.add(dependency)
                    }
                }
            }
        }
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
