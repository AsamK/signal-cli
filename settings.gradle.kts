dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            library("bouncycastle", "org.bouncycastle", "bcprov-jdk15on").version("1.70")
            library("jackson.databind", "com.fasterxml.jackson.core", "jackson-databind").version("2.14.2")
            library("argparse4j", "net.sourceforge.argparse4j", "argparse4j").version("0.9.0")
            library("dbusjava", "com.github.hypfvieh", "dbus-java-transport-native-unixsocket").version("4.3.0")
            version("slf4j", "2.0.7")
            library("slf4j.api", "org.slf4j", "slf4j-api").versionRef("slf4j")
            library("slf4j.jul", "org.slf4j", "jul-to-slf4j").versionRef("slf4j")
            library("logback", "ch.qos.logback", "logback-classic").version("1.4.6")


            library("signalservice", "com.github.turasa", "signal-service-java").version("2.15.3_unofficial_68")
            library("protobuf", "com.google.protobuf", "protobuf-javalite").version("3.22.2")
            library("sqlite", "org.xerial", "sqlite-jdbc").version("3.41.2.1")
            library("hikari", "com.zaxxer", "HikariCP").version("5.0.1")
            library("junit", "org.junit.jupiter", "junit-jupiter").version("5.9.2")
        }
    }
}

rootProject.name = "signal-cli"
include("lib")
