import org.gradle.api.artifacts.transform.*
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@CacheableTransform
abstract class JarFileExcluder : TransformAction<JarFileExcluder.Parameters> {
    interface Parameters : TransformParameters {
        @get:Input
        var excludeFilesByArtifact: Map<String, Set<String>>
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override
    fun transform(outputs: TransformOutputs) {
        val fileName = inputArtifact.get().asFile.name
        for (entry in parameters.excludeFilesByArtifact) {
            if (fileName.startsWith(entry.key)) {
                val nameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."))
                excludeFiles(inputArtifact.get().asFile, entry.value, outputs.file("${nameWithoutExtension}.jar"))
                return
            }
        }
        outputs.file(inputArtifact)
    }

    private fun excludeFiles(artifact: File, excludeFiles: Set<String>, jarFile: File) {
        ZipInputStream(FileInputStream(artifact)).use { input ->
            ZipOutputStream(FileOutputStream(jarFile)).use { output ->
                var entry = input.nextEntry
                while (entry != null) {
                    if (!excludeFiles.contains(entry.name)) {
                        output.putNextEntry(entry)
                        input.copyTo(output)
                        output.closeEntry()
                    }

                    entry = input.nextEntry
                }
            }
        }
    }
}
