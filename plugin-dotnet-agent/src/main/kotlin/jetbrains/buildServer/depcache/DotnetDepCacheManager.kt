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
    private val _invalidationDataCollector: DotnetDepCacheInvalidationDataCollector
) {

    val cache: DependencyCache?
        get() = _dotnetDepCacheSettingsProvider.cache

    val cacheEnabled: Boolean
        get() = cache != null // not null when cache is enabled and configured for dotnet runner

    fun prepareInvalidationDataAsync(workingDirectory: File, depCacheStepContext: DotnetDepCacheBuildStepContext) {
        val cache = cache
        val invalidator = _dotnetDepCacheSettingsProvider.postBuildInvalidator
        if (cache == null || invalidator == null) {
            // this is not an expected case, something is wrong
            _loggerService.writeWarning(".NET dependency cache is enabled but failed to initialize, couldn't prepare invalidation data")
            return
        }

        val deferred: Deferred<Map<String, String>> = _coroutineScope.async {
            withContext(Dispatchers.IO) {
                _invalidationDataCollector.collect(workingDirectory, cache, depCacheStepContext.depthLimit).fold(
                    onSuccess = { it },
                    onFailure = { exception ->
                        cache.logWarning("Error while preparing invalidation data, this execution will not be cached: ${exception.message}")
                        return@fold emptyMap()
                    }
                )
            }
        }

        depCacheStepContext.invalidationData = deferred
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

    fun updateInvalidationData(
        depCacheStepContext: DotnetDepCacheBuildStepContext
    ) {
        val cache = cache
        val invalidator = _dotnetDepCacheSettingsProvider.postBuildInvalidator
        if (cache == null || invalidator == null) {
            // this is not an expected case, something is wrong
            _loggerService.writeWarning(".NET dependency cache is enabled but failed to initialize, it will not be used at the current execution")
            return
        }

        if (depCacheStepContext.invalidationData == null) {
            cache.logWarning("invalidation data hasn't been prepared, this execution will not be cached")
            return
        }

        if (depCacheStepContext.cachesLocations.isEmpty()) {
            cache.logWarning("NuGet caches locations wasn't detected, this execution will not be cached")
            return
        }

        val invalidationData: Map<String, String> = runCatching {
            runBlocking {
                withTimeout(depCacheStepContext.invalidationDataAwaitTimeout) {
                    depCacheStepContext.invalidationData!!.await()
                }
            }
        }.getOrElse { e ->
            cache.logWarning("an error occurred during getting the invalidation data: ${e.message}")
            emptyMap()
        }

        if (invalidationData.isEmpty()) {
            cache.logWarning("invalidation data wasn't collected, this execution will not be cached")
            return
        }

        invalidator.addChecksumsToCachesLocations(depCacheStepContext.cachesLocations, invalidationData)
    }
}