package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRootUsage
import jetbrains.buildServer.agent.runner.ParameterType
import jetbrains.buildServer.agent.runner.ParametersService
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.PROJECT_FILES_CHECKSUM_AWAITING_TIMEOUT_DEFAULT_MS
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.PROJECT_FILES_CHECKSUM_AWAITING_TIMEOUT_MS
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.PROJECT_FILES_CHECKSUM_SEARCH_DEPTH_LIMIT
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.PROJECT_FILES_CHECKSUM_SEARCH_DEPTH_LIMIT_DEFAULT
import kotlinx.coroutines.Deferred
import java.nio.file.Path

class DotnetDepCacheBuildStepContext(private val _parametersService: ParametersService) {

    val cachesLocations: MutableSet<Path> = HashSet()

    var projectFilesChecksum: Deferred<String>? = null

    val projectFilesChecksumAwaitTimeout: Long
        get() = _parametersService.tryGetParameter(ParameterType.Configuration, PROJECT_FILES_CHECKSUM_AWAITING_TIMEOUT_MS)
            ?.toLongOrNull()
            ?: PROJECT_FILES_CHECKSUM_AWAITING_TIMEOUT_DEFAULT_MS

    val depthLimit: Int
        get() = _parametersService.tryGetParameter(ParameterType.Configuration, PROJECT_FILES_CHECKSUM_SEARCH_DEPTH_LIMIT)
            ?.toIntOrNull()
            ?: PROJECT_FILES_CHECKSUM_SEARCH_DEPTH_LIMIT_DEFAULT

    private var executionNumber = 0

    fun newCacheRootUsage(nugetPackagesPath: Path, stepId: String): CacheRootUsage {
        return CacheRootUsage(
            DotnetDependencyCacheConstants.CACHE_ROOT_TYPE,
            nugetPackagesPath.toAbsolutePath(),
            "$stepId.${executionNumber++}"
        )
    }
}