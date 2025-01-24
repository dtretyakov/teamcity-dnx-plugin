package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.cache.depcache.DependencyCache
import jetbrains.buildServer.util.FileUtil
import jetbrains.buildServer.util.Predicate
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern

class DotnetDepCacheInvalidationDataCollector {

    fun collect(workDir: File, cache: DependencyCache, depthLimit: Int): Result<Map<String, String>> {
        return runCatching {
            val files = ArrayList<String>()
            FileUtil.listFilesRecursively(workDir, File.separator, false, depthLimit, FILE_FILTER, files)
            val checksums = buildChecksums(files, workDir, cache)

            return Result.success(checksums)
        }
    }

    private fun buildChecksums(files: List<String>, workDir: File, cache: DependencyCache): Map<String, String> {
        val result = HashMap<String, String>()
        val messageDigest = MessageDigest.getInstance("SHA-256")

        for (filePath in files) {
            val targetFile = File(workDir, filePath)
            if (!targetFile.exists() || !targetFile.isFile) {
                cache.logWarning("File not found or is not a valid file: $targetFile")
                continue
            }

            val content = targetFile.readBytes()
            val digest = messageDigest.digest(content)

            result[filePath.replace(File.separatorChar, '/')] = digest.toHex()
        }

        return result
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