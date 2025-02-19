package jetbrains.buildServer.depcache

object DotnetDependencyCacheConstants {
    const val CACHE_ROOT_TYPE: String = "nuget-packages"
    const val CACHE_DISPLAY_NAME: String = "NuGet"
    const val FEATURE_TOGGLE_DOTNET_DEPENDENCY_CACHE: String = "teamcity.internal.depcache.buildFeature.dotnet.enabled"
    const val FEATURE_TOGGLE_DOTNET_DEPENDENCY_CACHE_DEFAULT: Boolean = false

    const val CACHE_ORIGINAL_ARCHIVE: String = "teamcity.internal.depcache.dotnet.cacheOriginalArchive"
    const val CACHE_ORIGINAL_ARCHIVE_DEFAULT: Boolean = false
    const val PROJECT_FILES_CHECKSUM_SEARCH_DEPTH_LIMIT: String = "teamcity.internal.depcache.dotnet.projectFilesChecksumSearchDepthLimit"
    const val PROJECT_FILES_CHECKSUM_SEARCH_DEPTH_LIMIT_DEFAULT: Int = Integer.MAX_VALUE
    const val PROJECT_FILES_CHECKSUM_AWAITING_TIMEOUT_MS: String = "teamcity.internal.depcache.dotnet.projectFilesChecksumAwaitingTimeoutMs"
    const val PROJECT_FILES_CHECKSUM_AWAITING_TIMEOUT_DEFAULT_MS: Long = 60000
}