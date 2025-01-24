package jetbrains.buildServer.depcache

object DotnetDependencyCacheConstants {
    const val CACHE_ROOT_TYPE: String = "nuget-packages"
    const val CACHE_DISPLAY_NAME: String = "NuGet"
    const val FEATURE_TOGGLE_DOTNET_DEPENDENCY_CACHE: String = "teamcity.internal.depcache.buildFeature.dotnet.enabled"
    const val FEATURE_TOGGLE_DOTNET_DEPENDENCY_CACHE_DEFAULT: Boolean = false

    const val CACHE_ORIGINAL_ARCHIVE: String = "teamcity.internal.depcache.dotnet.cacheOriginalArchive"
    const val CACHE_ORIGINAL_ARCHIVE_DEFAULT: Boolean = false
    const val INVALIDATION_DATA_SEARCH_DEPTH_LIMIT: String = "teamcity.internal.depcache.dotnet.invalidationDataSearchDepthLimit"
    const val INVALIDATION_DATA_SEARCH_DEPTH_LIMIT_DEFAULT: Int = Integer.MAX_VALUE
    const val INVALIDATION_DATA_AWAITING_TIMEOUT_MS: String = "teamcity.internal.depcache.dotnet.invalidationDataAwaitingTimeoutMs"
    const val INVALIDATION_DATA_AWAITING_TIMEOUT_DEFAULT_MS: Long = 60000
    const val THREAD_POOL_SIZE: String = "teamcity.internal.depcache.dotnet.threadPoolSize"
    const val THREAD_POOL_SIZE_DEFAULT: Int = 1
}