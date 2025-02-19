package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRoot
import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRootDescriptor
import jetbrains.buildServer.agent.cache.depcache.invalidation.DependencyCacheInvalidator
import jetbrains.buildServer.agent.cache.depcache.invalidation.InvalidationMetadata
import jetbrains.buildServer.agent.cache.depcache.invalidation.InvalidationResult
import java.nio.file.Path

class DotnetDepCachePackagesChangedInvalidator(
    private val checksumBuilder: DotnetDepCacheChecksumBuilder
) : DependencyCacheInvalidator {

    private val absoluteCachesPathToChecksum: MutableMap<String, String> = HashMap()

    override fun run(
        invalidationMetadata: InvalidationMetadata,
        cacheRoots: List<CacheRootDescriptor>,
        newCacheRoots: List<CacheRoot>
    ): InvalidationResult {
        if (absoluteCachesPathToChecksum.isEmpty()) {
            return InvalidationResult.invalidated("Checksum for dependencies wasn't computed")
        }

        val absoluteCachesPathToNewCacheRootId: Map<String, String> = newCacheRoots.associate {
            it.location.toAbsolutePath().toString() to it.id
        }

        val newCacheRootIdToFilesChecksum: Map<String, String> = absoluteCachesPathToChecksum.entries.mapNotNull { entry ->
            absoluteCachesPathToNewCacheRootId[entry.key]?.let { cacheRootId ->
                cacheRootId to entry.value
            }
        }.toMap()

        val newChecksum = DotnetDepCacheProjectFilesChecksum(newCacheRootIdToFilesChecksum)
        val cachedChecksum = invalidationMetadata.getObjectParameter("nugetProjectFilesChecksum") {
            DotnetDepCacheProjectFilesChecksum.deserialize(it)
        }

        invalidationMetadata.publishObjectParameter<DotnetDepCacheProjectFilesChecksum>("nugetProjectFilesChecksum", newChecksum)

        return if (newChecksum == cachedChecksum) InvalidationResult.validated()
        else InvalidationResult.invalidated("Nuget packages have changed")
    }

    override fun shouldRunIfCacheInvalidated(): Boolean = true

    fun addChecksumToCachesLocations(cachesLocations: Set<Path>, checksum: String) {
        cachesLocations.forEach { path ->
            val key = path.toAbsolutePath().toString()
            val existingChecksum = absoluteCachesPathToChecksum[key]

            if (existingChecksum == null) {
                absoluteCachesPathToChecksum[key] = checksum
                return
            }

            absoluteCachesPathToChecksum[key] = checksumBuilder.merge(existingChecksum, checksum)
        }
    }
}