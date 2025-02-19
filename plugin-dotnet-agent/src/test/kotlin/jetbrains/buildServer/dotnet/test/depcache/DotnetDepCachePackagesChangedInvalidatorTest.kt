package jetbrains.buildServer.dotnet.test.depcache

import io.mockk.*
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRoot
import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRootDescriptor
import jetbrains.buildServer.agent.cache.depcache.invalidation.Deserializer
import jetbrains.buildServer.agent.cache.depcache.invalidation.InvalidationMetadata
import jetbrains.buildServer.agent.cache.depcache.invalidation.InvalidationResult
import jetbrains.buildServer.agent.cache.depcache.invalidation.Serializable
import jetbrains.buildServer.depcache.DotnetDepCacheChecksumBuilder
import jetbrains.buildServer.depcache.DotnetDepCachePackagesChangedInvalidator
import jetbrains.buildServer.depcache.DotnetDepCacheProjectFilesChecksum
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths

class DotnetDepCachePackagesChangedInvalidatorTest {

    private lateinit var instance: DotnetDepCachePackagesChangedInvalidator
    @MockK private lateinit var invalidationMetadataMock: InvalidationMetadata
    @MockK private lateinit var checksumBuilder: DotnetDepCacheChecksumBuilder

    @BeforeMethod
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        clearAllMocks()
        instance = DotnetDepCachePackagesChangedInvalidator(checksumBuilder)
    }

    @DataProvider
    fun getPackagesNotChangedTestData(): Array<Array<Any>> {
        return arrayOf(
            arrayOf(
                """
                    {
                      "absoluteCachesPathToChecksum": {
                        "cache-root-1": "932710cf8b4e31b5dd242a72540fe51c2fb9510fedbeaf7866780843d39af699",
                        "cache-root-2": "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a"
                      }
                    }
                """.trimIndent(),
                listOf(
                    CacheRoot(
                        "cache-root-1",
                        DotnetDependencyCacheConstants.CACHE_ROOT_TYPE,
                        Paths.get("/nuget/.packages1"),
                        emptySet<String>()
                    ),
                    CacheRoot(
                        "cache-root-2",
                        DotnetDependencyCacheConstants.CACHE_ROOT_TYPE,
                        Paths.get("/nuget/.packages2"),
                        emptySet<String>()
                    )
                ),
                mapOf(
                    Paths.get("/nuget/.packages1") to "932710cf8b4e31b5dd242a72540fe51c2fb9510fedbeaf7866780843d39af699",
                    Paths.get("/nuget/.packages2") to "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a"
                )
            )
        )
    }

    @Test(dataProvider = "getPackagesNotChangedTestData")
    fun `should not invalidate cache when invalidation data not changed`(
        cachedPackagesJson: String,
        newCacheRoots: List<CacheRoot>,
        repoPathToPackages: Map<Path, String>
    ) {
        // arrange
        val parameterName = "nugetProjectFilesChecksum"
        val cachedPackages = prepareNugetPackagesMetadata(cachedPackagesJson)
        every { invalidationMetadataMock.getObjectParameter(any(), any<Deserializer<Serializable>>()) } returns cachedPackages
        val serializableArgumentSlot = slot<Serializable>()

        // act
        repoPathToPackages.forEach {
            instance.addChecksumToCachesLocations(setOf(it.key), it.value)
        }

        val invalidationResult: InvalidationResult = instance.run(invalidationMetadataMock, emptyList<CacheRootDescriptor>(), newCacheRoots)

        // assert
        Assert.assertFalse(invalidationResult.isInvalidated)
        Assert.assertNull(invalidationResult.invalidationReason)
        verify { invalidationMetadataMock.getObjectParameter(parameterName, any<Deserializer<Serializable>>()) }
        verify { invalidationMetadataMock.publishObjectParameter(parameterName, capture(serializableArgumentSlot)) }
        val capturedPackagesToPublish  = serializableArgumentSlot.captured as DotnetDepCacheProjectFilesChecksum
        Assert.assertEquals(capturedPackagesToPublish, cachedPackages) // we publish the same package sets
    }

    @DataProvider
    fun getPackagesChangedTestData(): Array<Array<Any>> {
        return arrayOf<Array<Any>>(
            arrayOf(
                """
                    {
                      "absoluteCachesPathToChecksum": {
                        "cache-root-1": "932710cf8b4e31b5dd242a72540fe51c2fb9510fedbeaf7866780843d39af699",
                        "cache-root-2": "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a"
                      }
                    }
                """.trimIndent(),
                listOf(
                    CacheRoot(
                        "cache-root-1",
                        DotnetDependencyCacheConstants.CACHE_ROOT_TYPE,
                        Paths.get("/nuget/.packages1"),
                        emptySet<String>()
                    ),
                    CacheRoot(
                        "cache-root-2",
                        DotnetDependencyCacheConstants.CACHE_ROOT_TYPE,
                        Paths.get("/nuget/.packages2"),
                        emptySet<String>()
                    )
                ),
                mapOf(
                    Paths.get("/nuget/.packages1") to "ae990de7ec4fa1af7ce5fc014f55623c34e15857baddf63b2dabc43fc9c5dec3",
                    Paths.get("/nuget/.packages2") to "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a"
                )
            ),
        )
    }

    @Test(dataProvider = "getPackagesChangedTestData")
    fun `should invalidate cache when package sets changed`(
        cachedPackagesJson: String,
        newCacheRoots: List<CacheRoot>,
        repoPathToPackages: Map<Path, String>
    ) {
        // arrange
        val parameterName = "nugetProjectFilesChecksum"
        var cachedPackages = prepareNugetPackagesMetadata(cachedPackagesJson)
        every { invalidationMetadataMock.getObjectParameter(any(), any<Deserializer<Serializable>>()) } returns cachedPackages
        val serializableArgumentSlot = slot<Serializable>()

        // act
        repoPathToPackages.forEach {
            instance.addChecksumToCachesLocations(setOf(it.key), it.value)
        }
        var invalidationResult: InvalidationResult = instance.run(invalidationMetadataMock, emptyList<CacheRootDescriptor>(), newCacheRoots)

        // assert
        Assert.assertTrue(invalidationResult.isInvalidated)
        Assert.assertNotNull(invalidationResult.invalidationReason)
        verify { invalidationMetadataMock.getObjectParameter(parameterName, any<Deserializer<Serializable>>()) }
        verify { invalidationMetadataMock.publishObjectParameter(parameterName, capture(serializableArgumentSlot)) }
        var capturedPackagesToPublish = serializableArgumentSlot.captured as DotnetDepCacheProjectFilesChecksum?
        Assert.assertNotEquals(capturedPackagesToPublish, cachedPackages) // we publish changed package sets
    }

    private fun prepareNugetPackagesMetadata(json: String): DotnetDepCacheProjectFilesChecksum {
        return DotnetDepCacheProjectFilesChecksum.deserialize(json.toByteArray(StandardCharsets.UTF_8))
    }
}