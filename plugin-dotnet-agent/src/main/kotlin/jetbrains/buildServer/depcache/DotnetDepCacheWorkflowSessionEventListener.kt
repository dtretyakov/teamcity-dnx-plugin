package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.BuildFinishedStatus
import jetbrains.buildServer.agent.runner.*

class DotnetDepCacheWorkflowSessionEventListener(
    private val _pathsService: PathsService,
    private val _loggerService: LoggerService,
    private val _parametersService: ParametersService,
    private val _dependencyCacheManager: DotnetDepCacheManager,
    private val _buildStepContextHolder: DotnetDepCacheBuildStepContextHolder
) : WorkflowSessionEventListener {

    override fun onSessionStarted() {
        if (!_dependencyCacheManager.cacheEnabled) {
            return
        }

        val workingDir = _pathsService.getPath(PathType.WorkingDirectory)
        _buildStepContextHolder.initContext()

        _dependencyCacheManager.prepareChecksumAsync(workingDir, _buildStepContextHolder.context!!)
    }

    override fun onSessionFinished(status: BuildFinishedStatus) {
        if (!_dependencyCacheManager.cacheEnabled) {
            return
        }

        if (_buildStepContextHolder.context == null) {
            _loggerService.writeWarning("Nuget cache step context wasn't initialized, this execution won't be cached")
            return
        }

        _dependencyCacheManager.updateInvalidatorWithChecksum(_buildStepContextHolder.context!!)
        _buildStepContextHolder.clearContext()
    }
}