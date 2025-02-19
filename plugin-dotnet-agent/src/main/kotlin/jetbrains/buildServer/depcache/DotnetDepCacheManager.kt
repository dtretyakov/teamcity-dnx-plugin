package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.CommandLineOutputAccumulationObserver
import jetbrains.buildServer.agent.cache.depcache.DependencyCache
import jetbrains.buildServer.agent.runner.BuildInfo
import jetbrains.buildServer.agent.runner.LoggerService
import jetbrains.buildServer.depcache.utils.DotnetDepCacheGlobalPackagesLocationParser
import kotlinx.coroutines.*
import java.io.File

class DotnetDepCacheManager(
    private val _loggerService: LoggerService,
    private val _dotnetDepCacheSettingsProvider: DotnetDepCacheSettingsProvider,
    private val _buildInfo: BuildInfo,
    private val _coroutineScope: CoroutineScope,
    private val _checksumBuilder: DotnetDepCacheChecksumBuilder
) {

    val cache: DependencyCache?
        get() = _dotnetDepCacheSettingsProvider.cache

    val cacheEnabled: Boolean
        get() = cache != null // not null when cache is enabled and configured for dotnet runner

    fun prepareChecksumAsync(workingDirectory: File, depCacheStepContext: DotnetDepCacheBuildStepContext) {
        val cache = cache
        val invalidator = _dotnetDepCacheSettingsProvider.postBuildInvalidator
        if (cache == null || invalidator == null) {
            // this is not an expected case, something is wrong
            _loggerService.writeWarning(".NET dependency cache is enabled but failed to initialize, couldn't prepare a checksum")
            return
        }

        val deferred: Deferred<String> = _coroutineScope.async {
            withContext(Dispatchers.IO) {
                _checksumBuilder.build(workingDirectory, cache, depCacheStepContext.depthLimit).fold(
                    onSuccess = { it },
                    onFailure = { exception ->
                        cache.logWarning("Error while preparing a checksum, this execution will not be cached: ${exception.message}")
                        return@fold ""
                    }
                )
            }
        }

        depCacheStepContext.projectFilesChecksum = deferred
    }

    fun registerAndRestoreCache(
        depCacheContext: DotnetDepCacheBuildStepContext,
        nugetPackagesGlobalDirObserver: CommandLineOutputAccumulationObserver
    ) {
        val packagesRawOutput = nugetPackagesGlobalDirObserver.output
        if (packagesRawOutput.isNullOrEmpty()) {
            cache?.logWarning("Failed to detect global nuget packages location for the current .NET execution, it will not be cached")
            return
        }
        val globalNugetPackagesLocation = DotnetDepCacheGlobalPackagesLocationParser.fromCommandLineOutput(packagesRawOutput)
        if (globalNugetPackagesLocation == null) {
            cache?.logWarning("""
                Failed to parse global nuget packages location from: $packagesRawOutput.
                The current .NET execution will not be cached
            """.trimIndent())
            return
        }

        registerAndRestoreCache(depCacheContext, File(globalNugetPackagesLocation))
    }

    fun registerAndRestoreCache(
        depCacheContext: DotnetDepCacheBuildStepContext,
        nugetPackagesLocation: File
    ) {
        val cache = cache
        val invalidator = _dotnetDepCacheSettingsProvider.postBuildInvalidator
        if (cache == null || invalidator == null) {
            // this is not an expected case, something is wrong
            _loggerService.writeWarning(".NET dependency cache is enabled but failed to initialize, it will not be used at the current execution")
            return
        }

        if (!nugetPackagesLocation.exists()) {
            _loggerService.writeDebug("Nuget packages location doesn't exist, creating it: ${nugetPackagesLocation.absolutePath}")
            nugetPackagesLocation.mkdirs()
        }
        val nugetPackagesPath = nugetPackagesLocation.toPath()
        val nupkgEmptyFile = File(nugetPackagesLocation, "teamcity-nuget-cache-empty-file.nupkg")
        if (!nupkgEmptyFile.exists()) {
            nupkgEmptyFile.createNewFile()
        }

        _loggerService.writeDebug("Creating a new cache root usage for nuget packages location: $nugetPackagesPath")
        val cacheRootUsage = depCacheContext.newCacheRootUsage(nugetPackagesPath, _buildInfo.id)
        cache.registerAndRestore(cacheRootUsage)

        depCacheContext.cachesLocations.add(nugetPackagesPath)
    }

    fun updateInvalidatorWithChecksum(depCacheStepContext: DotnetDepCacheBuildStepContext) {
        val cache = cache
        val invalidator = _dotnetDepCacheSettingsProvider.postBuildInvalidator
        if (cache == null || invalidator == null) {
            // this is not an expected case, something is wrong
            _loggerService.writeWarning(".NET dependency cache is enabled but failed to initialize, it will not be used at the current execution")
            return
        }

        if (depCacheStepContext.projectFilesChecksum == null) {
            cache.logWarning("Checksum hasn't been built, this execution will not be cached")
            return
        }

        if (depCacheStepContext.cachesLocations.isEmpty()) {
            cache.logWarning("NuGet caches locations wasn't detected, this execution will not be cached")
            return
        }

        val projectFilesChecksum: String = runCatching {
            runBlocking {
                withTimeout(depCacheStepContext.projectFilesChecksumAwaitTimeout) {
                    depCacheStepContext.projectFilesChecksum!!.await()
                }
            }
        }.getOrElse { e ->
            cache.logWarning("an error occurred during getting the project files checksum: ${e.message}")
            ""
        }

        if (projectFilesChecksum.isEmpty()) {
            cache.logWarning("checksum wasn't built, this execution will not be cached")
            return
        }

        invalidator.addChecksumToCachesLocations(depCacheStepContext.cachesLocations, projectFilesChecksum)
    }
}