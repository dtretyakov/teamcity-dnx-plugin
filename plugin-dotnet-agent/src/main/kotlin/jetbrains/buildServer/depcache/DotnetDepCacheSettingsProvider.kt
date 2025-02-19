package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildFinishedStatus
import jetbrains.buildServer.agent.cache.depcache.DependencyCacheProvider
import jetbrains.buildServer.agent.cache.depcache.DependencyCacheSettingsProviderRegistry
import jetbrains.buildServer.agent.cache.depcache.buildFeature.BuildRunnerDependencyCacheSettingsProvider
import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRootPublishPaths
import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRootPublisher
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.CACHE_ORIGINAL_ARCHIVE
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.CACHE_ORIGINAL_ARCHIVE_DEFAULT
import jetbrains.buildServer.dotnet.DotnetConstants
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.util.EventDispatcher

class DotnetDepCacheSettingsProvider(
    private val eventDispatcher: EventDispatcher<AgentLifeCycleListener>,
    private val cacheSettingsProviderRegistry: DependencyCacheSettingsProviderRegistry,
    private val cacheProvider: DependencyCacheProvider,
    private val checksumBuilder: DotnetDepCacheChecksumBuilder
) : BuildRunnerDependencyCacheSettingsProvider(
    eventDispatcher, cacheSettingsProviderRegistry, cacheProvider,
    DotnetConstants.RUNNER_TYPE,
    DotnetConstants.RUNNER_DISPLAY_NAME,
    DotnetDependencyCacheConstants.CACHE_DISPLAY_NAME,
    DotnetDependencyCacheConstants.FEATURE_TOGGLE_DOTNET_DEPENDENCY_CACHE,
    DotnetDependencyCacheConstants.FEATURE_TOGGLE_DOTNET_DEPENDENCY_CACHE_DEFAULT
) {
    var postBuildInvalidator: DotnetDepCachePackagesChangedInvalidator? = null
        private set

    protected override fun createPostBuildInvalidators(): List<DotnetDepCachePackagesChangedInvalidator> {
        postBuildInvalidator = DotnetDepCachePackagesChangedInvalidator(checksumBuilder)
        return listOf(postBuildInvalidator!!)
    }

    protected override fun createCacheRootPublisher(): CacheRootPublisher {
        val cacheOriginalArchive = TeamCityProperties.getBoolean(CACHE_ORIGINAL_ARCHIVE, CACHE_ORIGINAL_ARCHIVE_DEFAULT)

        if (cacheOriginalArchive) {
            return CacheRootPublisher({
                CacheRootPublishPaths.includeAll()
            })
        }

        return CacheRootPublisher({
            CacheRootPublishPaths.exclude(
                listOf("**/*.nupkg")
            )
        })
    }

    public override fun buildFinished(build: AgentRunningBuild, buildStatus: BuildFinishedStatus) {
        postBuildInvalidator = null
    }
}