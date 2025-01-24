package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRootUsage
import jetbrains.buildServer.agent.runner.ParameterType
import jetbrains.buildServer.agent.runner.ParametersService
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.INVALIDATION_DATA_AWAITING_TIMEOUT_DEFAULT_MS
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.INVALIDATION_DATA_AWAITING_TIMEOUT_MS
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.INVALIDATION_DATA_SEARCH_DEPTH_LIMIT
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.INVALIDATION_DATA_SEARCH_DEPTH_LIMIT_DEFAULT
import kotlinx.coroutines.Deferred
import java.nio.file.Path

class DotnetDepCacheBuildStepContext(private val _parametersService: ParametersService) {

    val cachesLocations: MutableSet<Path> = HashSet()

    var invalidationData: Deferred<Map<String, String>>? = null

    val invalidationDataAwaitTimeout: Long
        get() = _parametersService.tryGetParameter(ParameterType.Configuration, INVALIDATION_DATA_AWAITING_TIMEOUT_MS)
            ?.toLongOrNull()
            ?: INVALIDATION_DATA_AWAITING_TIMEOUT_DEFAULT_MS

    val depthLimit: Int
        get() = _parametersService.tryGetParameter(ParameterType.Configuration, INVALIDATION_DATA_SEARCH_DEPTH_LIMIT)
            ?.toIntOrNull()
            ?: INVALIDATION_DATA_SEARCH_DEPTH_LIMIT_DEFAULT

    private var executionNumber = 0

    fun newCacheRootUsage(nugetPackagesPath: Path, stepId: String): CacheRootUsage {
        return CacheRootUsage(
            DotnetDependencyCacheConstants.CACHE_ROOT_TYPE,
            nugetPackagesPath.toAbsolutePath(),
            "$stepId.${executionNumber++}"
        )
    }

    companion object {
        fun newContext(parametersService: ParametersService): DotnetDepCacheBuildStepContext = DotnetDepCacheBuildStepContext(parametersService)
    }
}