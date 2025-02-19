package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.runner.ParametersService

class DotnetDepCacheBuildStepContextHolder(
    private val _parametersService: ParametersService
) {

    var context: DotnetDepCacheBuildStepContext? = null
        private set

    fun initContext() {
        context = DotnetDepCacheBuildStepContext(_parametersService)
    }

    fun clearContext() {
        context = null
    }
}