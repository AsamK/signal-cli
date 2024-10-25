dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            library("bouncycastle", "org.bouncycastle", "bcprov-jdk18on").version("1.78.1")
            library("jackson.databind", "com.fasterxml.jackson.core", "jackson-databind").version("2.18.0")
            library("argparse4j", "net.sourceforge.argparse4j", "argparse4j").version("0.9.0")
            library("dbusjava", "com.github.hypfvieh", "dbus-java-transport-native-unixsocket").version("5.0.0")
            version("slf4j", "2.0.16")
            library("slf4j.api", "org.slf4j", "slf4j-api").versionRef("slf4j")
            library("slf4j.jul", "org.slf4j", "jul-to-slf4j").versionRef("slf4j")
            library("logback", "ch.qos.logback", "logback-classic").version("1.5.11")

            library("signalservice", "com.github.turasa", "signal-service-java").version("2.15.3_unofficial_110")
            library("sqlite", "org.xerial", "sqlite-jdbc").version("3.47.0.0")
            library("hikari", "com.zaxxer", "HikariCP").version("6.0.0")
            library("junit.jupiter", "org.junit.jupiter", "junit-jupiter").version("5.11.3")
            library("junit.launcher", "org.junit.platform", "junit-platform-launcher").version("1.11.3")
        }
    }
}

rootProject.name = "signal-cli"
include("lib")
