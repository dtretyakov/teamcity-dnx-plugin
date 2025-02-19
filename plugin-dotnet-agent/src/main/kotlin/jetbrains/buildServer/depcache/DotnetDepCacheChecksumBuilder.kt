package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.cache.depcache.DependencyCache
import jetbrains.buildServer.util.FileUtil
import jetbrains.buildServer.util.Predicate
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern

class DotnetDepCacheChecksumBuilder {

    fun merge(first: String, second: String): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")

        val combined = (first + second).toByteArray()

        return messageDigest.digest(combined).toHex()
    }

    fun build(workDir: File, cache: DependencyCache, depthLimit: Int): Result<String> {
        return runCatching {
            val files = ArrayList<String>()
            FileUtil.listFilesRecursively(workDir, File.separator, false, depthLimit, FILE_FILTER, files)
            val checksum = buildByFiles(files, workDir, cache)

            return Result.success(checksum)
        }
    }

    private fun buildByFiles(files: List<String>, workDir: File, cache: DependencyCache): String {
        cache.logMessage("building a checksum: filesCount=${files.size}, workingDirectory=${workDir}")

        val messageDigest = MessageDigest.getInstance("SHA-256")
        val sortedFiles = files.sorted() // making hashing consistent

        for (filePath in sortedFiles) {
            val targetFile = File(workDir, filePath)
            if (!targetFile.exists() || !targetFile.isFile) {
                cache.logWarning("File not found or is not a valid file: $targetFile")
                continue
            }

            val content = targetFile.readText()
                .replace("\r\n", "\n") // CRLF --> LF to make checksums platform independent
                .toByteArray()
            messageDigest.update(content)
        }

        return messageDigest.digest().toHex()
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val REGEX: String = "(.+\\.csproj)" +  // Matches any .csproj files
                "|(Directory\\.Build\\.props)" +             // Matches Directory.Build.props
                "|(Directory\\.Build\\.targets)" +           // Matches Directory.Build.targets
                "|((?i)nuget\\.config)"                      // Case-insensitive matching NuGet.config, NuGet.Config and nuget.config files
        private val FILENAME_PATTERN = Pattern.compile(REGEX)
        private val FILE_FILTER = Predicate({ file: File ->
            file.isDirectory || FILENAME_PATTERN.matcher(file.name).matches()
        })
    }
}