import groovy.util.XmlSlurper
import groovy.util.slurpersupport.GPathResult
import org.codehaus.groovy.runtime.ResourceGroovyMethods
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency

class CheckLibVersionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.task("checkLibVersions") {
            description = "Find any 3rd party libraries which have released new versions to the central Maven repo since we last upgraded."
            doLast {
                project.configurations.flatMap { it.allDependencies }
                        .toSet()
                        .forEach { checkDependency(it) }
            }
        }
    }

    private fun Task.checkDependency(dependency: Dependency) {
        val version = dependency.version
        val group = dependency.group
        val path = group?.replace(".", "/") ?: ""
        val name = dependency.name
        val metaDataUrl = "https://repo1.maven.org/maven2/$path/$name/maven-metadata.xml"
        try {
            val url = ResourceGroovyMethods.toURL(metaDataUrl)
            val metaDataText = ResourceGroovyMethods.getText(url)
            val metadata = XmlSlurper().parseText(metaDataText)
            val newest = (metadata.getProperty("versioning") as GPathResult).getProperty("latest")
            if (version != newest.toString()) {
                println("UPGRADE {\"group\": \"$group\", \"name\": \"$name\", \"current\": \"$version\", \"latest\": \"$newest\"}")
            }
        } catch (e: Throwable) {
            logger.debug("Unable to download or parse $metaDataUrl: $e.message")
        }
    }
}
