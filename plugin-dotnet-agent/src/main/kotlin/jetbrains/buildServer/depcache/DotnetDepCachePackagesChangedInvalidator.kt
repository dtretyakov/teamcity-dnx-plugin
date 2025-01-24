package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRoot
import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRootDescriptor
import jetbrains.buildServer.agent.cache.depcache.invalidation.DependencyCacheInvalidator
import jetbrains.buildServer.agent.cache.depcache.invalidation.InvalidationMetadata
import jetbrains.buildServer.agent.cache.depcache.invalidation.InvalidationResult
import java.nio.file.Path

class DotnetDepCachePackagesChangedInvalidator : DependencyCacheInvalidator {

    private val absoluteCachesPathToFilePathToChecksum: MutableMap<String, Map<String, String>> = HashMap()

    override fun run(
        invalidationMetadata: InvalidationMetadata,
        cacheRoots: List<CacheRootDescriptor>,
        newCacheRoots: List<CacheRoot>
    ): InvalidationResult {
        if (absoluteCachesPathToFilePathToChecksum.isEmpty()) {
            return InvalidationResult.invalidated("Checksum for dependencies wasn't computed")
        }

        val absoluteCachesPathToNewCacheRootId: Map<String, String> = newCacheRoots.associate {
            it.location.toAbsolutePath().toString() to it.id
        }

        val newCacheRootIdToFilesChecksum: Map<String, Map<String, String>> = absoluteCachesPathToFilePathToChecksum.entries.mapNotNull { entry ->
            absoluteCachesPathToNewCacheRootId[entry.key]?.let { cacheRootId ->
                cacheRootId to entry.value
            }
        }.toMap()

        val newData = DotnetDepCacheInvalidationData(newCacheRootIdToFilesChecksum)
        val cachedData = invalidationMetadata.getObjectParameter("nugetInvalidationData") {
            DotnetDepCacheInvalidationData.deserialize(it)
        }

        invalidationMetadata.publishObjectParameter<DotnetDepCacheInvalidationData>("nugetInvalidationData", newData)

        return if (newData == cachedData) InvalidationResult.validated()
        else InvalidationResult.invalidated("Nuget packages have changed")
    }

    override fun shouldRunIfCacheInvalidated(): Boolean = true

    fun addChecksumsToCachesLocations(cachesLocations: Set<Path>, checksums: Map<String, String>) {
        cachesLocations.forEach { path ->
            val key = path.toAbsolutePath().toString()
            val existingChecksums = absoluteCachesPathToFilePathToChecksum[key]?.toMutableMap() ?: mutableMapOf()
            existingChecksums.putAll(checksums)
            absoluteCachesPathToFilePathToChecksum[key] = existingChecksums
        }
    }
}