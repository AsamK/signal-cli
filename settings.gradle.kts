dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "signal-cli"

include("libsignal-cli")
project(":libsignal-cli").projectDir = file("lib")
