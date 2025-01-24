package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.runner.ParameterType
import jetbrains.buildServer.agent.runner.ParametersService
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.INVALIDATION_DATA_AWAITING_TIMEOUT_DEFAULT_MS
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.INVALIDATION_DATA_AWAITING_TIMEOUT_MS
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.INVALIDATION_DATA_SEARCH_DEPTH_LIMIT
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.INVALIDATION_DATA_SEARCH_DEPTH_LIMIT_DEFAULT
import kotlinx.coroutines.Deferred

class DotnetDepCacheBuildStepContextHolder(
    private val _parametersService: ParametersService
) {

    var context: DotnetDepCacheBuildStepContext? = null
        private set

    fun initContext() {
        context = DotnetDepCacheBuildStepContext.newContext(_parametersService)
    }

    fun clearContext() {
        context = null
    }
}