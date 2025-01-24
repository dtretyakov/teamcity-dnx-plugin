package jetbrains.buildServer.dotnet.test.depcache

import io.mockk.*
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRoot
import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRootDescriptor
import jetbrains.buildServer.agent.cache.depcache.invalidation.Deserializer
import jetbrains.buildServer.agent.cache.depcache.invalidation.InvalidationMetadata
import jetbrains.buildServer.agent.cache.depcache.invalidation.InvalidationResult
import jetbrains.buildServer.agent.cache.depcache.invalidation.Serializable
import jetbrains.buildServer.depcache.DotnetDepCacheInvalidationData
import jetbrains.buildServer.depcache.DotnetDepCachePackagesChangedInvalidator
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

    @BeforeMethod
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        clearAllMocks()
        instance = DotnetDepCachePackagesChangedInvalidator()
    }

    @DataProvider
    fun getPackagesNotChangedTestData(): Array<Array<Any>> {
        return arrayOf(
            arrayOf(
                """
                    {
                      "absoluteCachesPathToFilePathToChecksum": {
                        "cache-root-1": {
                          "/Project1.csproj": "932710cf8b4e31b5dd242a72540fe51c2fb9510fedbeaf7866780843d39af699",
                          "/Prj2/Directory.Build.props": "ae990de7ec4fa1af7ce5fc014f55623c34e15857baddf63b2dabc43fc9c5dec3"
                        },
                        "cache-root-2": {
                          "/nuget.config": "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a"
                        }
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
                    Paths.get("/nuget/.packages1") to mapOf(
                        "/Project1.csproj" to "932710cf8b4e31b5dd242a72540fe51c2fb9510fedbeaf7866780843d39af699",
                        "/Prj2/Directory.Build.props" to "ae990de7ec4fa1af7ce5fc014f55623c34e15857baddf63b2dabc43fc9c5dec3"
                    ),
                    Paths.get("/nuget/.packages2") to mapOf(
                        "/nuget.config" to "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a"
                    )
                )
            )
        )
    }

    @Test(dataProvider = "getPackagesNotChangedTestData")
    fun `should not invalidate cache when invalidation data not changed`(
        cachedPackagesJson: String,
        newCacheRoots: List<CacheRoot>,
        repoPathToPackages: Map<Path, Map<String, String>>
    ) {
        // arrange
        val parameterName = "nugetInvalidationData"
        val cachedPackages = prepareNugetPackagesMetadata(cachedPackagesJson)
        every { invalidationMetadataMock.getObjectParameter(any(), any<Deserializer<Serializable>>()) } returns cachedPackages
        val serializableArgumentSlot = slot<Serializable>()

        // act
        repoPathToPackages.forEach {
            instance.addChecksumsToCachesLocations(setOf(it.key), it.value)
        }

        val invalidationResult: InvalidationResult = instance.run(invalidationMetadataMock, emptyList<CacheRootDescriptor>(), newCacheRoots)

        // assert
        Assert.assertFalse(invalidationResult.isInvalidated)
        Assert.assertNull(invalidationResult.invalidationReason)
        verify { invalidationMetadataMock.getObjectParameter(parameterName, any<Deserializer<Serializable>>()) }
        verify { invalidationMetadataMock.publishObjectParameter(parameterName, capture(serializableArgumentSlot)) }
        val capturedPackagesToPublish  = serializableArgumentSlot.captured as DotnetDepCacheInvalidationData
        Assert.assertEquals(capturedPackagesToPublish, cachedPackages) // we publish the same package sets
    }

    @DataProvider
    fun getPackagesChangedTestData(): Array<Array<Any>> {
        return arrayOf<Array<Any>>(
            // // set 1: checksum changed
            arrayOf(
                """
                    {
                      "absoluteCachesPathToFilePathToChecksum": {
                        "cache-root-1": {
                          "/Project1.csproj": "932710cf8b4e31b5dd242a72540fe51c2fb9510fedbeaf7866780843d39af699",
                          "/Prj2/Directory.Build.props": "ae990de7ec4fa1af7ce5fc014f55623c34e15857baddf63b2dabc43fc9c5dec3"
                        },
                        "cache-root-2": {
                          "/nuget.config": "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a"
                        }
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
                    Paths.get("/nuget/.packages1") to mapOf(
                        "/Project1.csproj" to "a2c9f2dafa9e40885d7109e3e5547fa602306d71f870e0d3e6245b99cccb432f",  // <-- new checksum
                        "/Prj2/Directory.Build.props" to "ae990de7ec4fa1af7ce5fc014f55623c34e15857baddf63b2dabc43fc9c5dec3"
                    ),
                    Paths.get("/nuget/.packages2") to mapOf(
                        "/nuget.config" to "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a"
                    )
                )
            ),

//            // set 2: a new file added
            arrayOf(
                """
                    {
                      "absoluteCachesPathToFilePathToChecksum": {
                        "cache-root-1": {
                          "/Project1.csproj": "932710cf8b4e31b5dd242a72540fe51c2fb9510fedbeaf7866780843d39af699",
                          "/Prj2/Directory.Build.props": "ae990de7ec4fa1af7ce5fc014f55623c34e15857baddf63b2dabc43fc9c5dec3"
                        },
                        "cache-root-2": {
                          "/nuget.config": "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a"
                        }
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
                    Paths.get("/nuget/.packages1") to mapOf(
                        "/Project1.csproj" to "a2c9f2dafa9e40885d7109e3e5547fa602306d71f870e0d3e6245b99cccb432f",
                        "/Prj2/Directory.Build.props" to "ae990de7ec4fa1af7ce5fc014f55623c34e15857baddf63b2dabc43fc9c5dec3"
                    ),
                    Paths.get("/nuget/.packages2") to mapOf(
                        "/nuget.config" to "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a",
                        "/Directory.Build.targets" to "898c9a78530f745e304de1ae51809d5a5cf1771a976bbc4d5c217eb48dea1ba7" // <-- the new file
                    )
                )
            ),

//            // set 3: a file deleted
            arrayOf(
                """
                    {
                      "absoluteCachesPathToFilePathToChecksum": {
                        "cache-root-1": {
                          "/Project1.csproj": "932710cf8b4e31b5dd242a72540fe51c2fb9510fedbeaf7866780843d39af699",
                          "/Prj2/Directory.Build.props": "ae990de7ec4fa1af7ce5fc014f55623c34e15857baddf63b2dabc43fc9c5dec3"
                        },
                        "cache-root-2": {
                          "/nuget.config": "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a",
                          "/Directory.Build.targets": "898c9a78530f745e304de1ae51809d5a5cf1771a976bbc4d5c217eb48dea1ba7"// <-- the deleted file
                        }
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
                    Paths.get("/nuget/.packages1") to mapOf(
                        "/Project1.csproj" to "a2c9f2dafa9e40885d7109e3e5547fa602306d71f870e0d3e6245b99cccb432f",
                        "/Prj2/Directory.Build.props" to "ae990de7ec4fa1af7ce5fc014f55623c34e15857baddf63b2dabc43fc9c5dec3"
                    ),
                    Paths.get("/nuget/.packages2") to mapOf(
                        "/nuget.config" to "994e24667e8b8412cb2b4ca645bd69c54ee2490dde5d727f2c835d809a7c386a"
                    )
                )
            ),
        )
    }

    @Test(dataProvider = "getPackagesChangedTestData")
    fun `should invalidate cache when package sets changed`(
        cachedPackagesJson: String,
        newCacheRoots: List<CacheRoot>,
        repoPathToPackages: Map<Path, Map<String, String>>
    ) {
        // arrange
        val parameterName = "nugetInvalidationData"
        var cachedPackages = prepareNugetPackagesMetadata(cachedPackagesJson)
        every { invalidationMetadataMock.getObjectParameter(any(), any<Deserializer<Serializable>>()) } returns cachedPackages
        val serializableArgumentSlot = slot<Serializable>()

        // act
        repoPathToPackages.forEach {
            instance.addChecksumsToCachesLocations(setOf(it.key), it.value)
        }
        var invalidationResult: InvalidationResult = instance.run(invalidationMetadataMock, emptyList<CacheRootDescriptor>(), newCacheRoots)

        // assert
        Assert.assertTrue(invalidationResult.isInvalidated)
        Assert.assertNotNull(invalidationResult.invalidationReason)
        verify { invalidationMetadataMock.getObjectParameter(parameterName, any<Deserializer<Serializable>>()) }
        verify { invalidationMetadataMock.publishObjectParameter(parameterName, capture(serializableArgumentSlot)) }
        var capturedPackagesToPublish = serializableArgumentSlot.captured as DotnetDepCacheInvalidationData?
        Assert.assertNotEquals(capturedPackagesToPublish, cachedPackages) // we publish changed package sets
    }

    private fun prepareNugetPackagesMetadata(json: String): DotnetDepCacheInvalidationData {
        return DotnetDepCacheInvalidationData.deserialize(json.toByteArray(StandardCharsets.UTF_8))
    }
}