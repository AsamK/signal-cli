dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            name = "SignalBuildArtifacts"
            url = uri("https://build-artifacts.signal.org/libraries/maven/")
            content {
                includeGroupByRegex("org\\.signal.*")
            }
        }
    }
}

rootProject.name = "signal-cli"

include("libsignal-cli")
project(":libsignal-cli").projectDir = file("lib")
