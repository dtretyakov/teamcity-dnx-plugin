package jetbrains.buildServer.dotnet.test.depcache

import io.mockk.*
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.TempFiles
import jetbrains.buildServer.agent.CommandLineOutputAccumulationObserver
import jetbrains.buildServer.agent.cache.depcache.DependencyCache
import jetbrains.buildServer.agent.cache.depcache.cacheroot.CacheRootUsage
import jetbrains.buildServer.agent.runner.BuildInfo
import jetbrains.buildServer.agent.runner.LoggerService
import jetbrains.buildServer.depcache.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File
import java.nio.file.Path
import kotlin.random.Random

class DotnetDepCacheManagerTest {
    @MockK
    private lateinit var _loggerService: LoggerService
    @MockK
    private lateinit var _dotnetDepCacheSettingsProvider: DotnetDepCacheSettingsProvider
    @MockK
    private lateinit var _buildInfo: BuildInfo
    @MockK
    private lateinit var _cache: DependencyCache
    @MockK
    private lateinit var _invalidator: DotnetDepCachePackagesChangedInvalidator
    @MockK
    private lateinit var _invalidationDataCollector: DotnetDepCacheInvalidationDataCollector

    private lateinit var tempFiles: TempFiles
    private lateinit var workDir: File
    private lateinit var cachesDir: File
    private lateinit var stepId: String
    private val invalidationData: Map<String, String> = mapOf(
        "/Project1.csproj" to "932710cf8b4e31b5dd242a72540fe51c2fb9510fedbeaf7866780843d39af699",
        "/Project2.csproj" to "ae990de7ec4fa1af7ce5fc014f55623c34e15857baddf63b2dabc43fc9c5dec3"
    )
    private lateinit var coroutineScope: CoroutineScope
    private val testDispatcher = StandardTestDispatcher()

    @BeforeMethod
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        clearAllMocks()
        tempFiles = TempFiles()
        cachesDir = File(tempFiles.createTempDir(), "packages")
        stepId = "dotnet_${Random.nextInt()}"
        workDir = tempFiles.createTempDir()
        Dispatchers.setMain(testDispatcher)
        coroutineScope = CoroutineScope(testDispatcher)

        every { _dotnetDepCacheSettingsProvider.cache } returns _cache
        every { _dotnetDepCacheSettingsProvider.postBuildInvalidator } returns _invalidator
        every { _buildInfo.id } returns stepId
        every { _invalidationDataCollector.collect(workDir, _cache, any()) } returns Result.success(invalidationData)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterMethod
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should register and restore cache`() {
        // arrange
        val cacheRootUsage = cacheRootUsage()
        val context = mockk<DotnetDepCacheBuildStepContext>(relaxed = true)
        val nugetPackagesGlobalDirObserver = mockk<CommandLineOutputAccumulationObserver>()
        every { nugetPackagesGlobalDirObserver.output } returns "global-packages: ${cachesDir.absolutePath}"
        every { context.newCacheRootUsage(any(), any()) } returns cacheRootUsage
        val manager = create()

        // act
        manager.registerAndRestoreCache(context, nugetPackagesGlobalDirObserver)

        // assert
        verify(exactly = 1) { context.newCacheRootUsage(cachesDir.toPath(), stepId) }
        verify(exactly = 1) { _cache.registerAndRestore(cacheRootUsage) }
    }

    @Test
    fun `should register and restore cache by exact location`() {
        // arrange
        val cacheRootUsage = cacheRootUsage()
        val context = mockk<DotnetDepCacheBuildStepContext>(relaxed = true)
        every { context.newCacheRootUsage(any(), any()) } returns cacheRootUsage
        val manager = create()

        // act
        manager.registerAndRestoreCache(context, cachesDir)

        // assert
        verify(exactly = 1) { context.newCacheRootUsage(cachesDir.toPath(), stepId) }
        verify(exactly = 1) { _cache.registerAndRestore(cacheRootUsage) }
    }

    @Test
    fun `should not register and restore cache when cache is disabled`() {
        // arrange
        val context = mockk<DotnetDepCacheBuildStepContext>()
        val nugetPackagesGlobalDirObserver = mockk<CommandLineOutputAccumulationObserver>()
        val manager = create()
        every { _dotnetDepCacheSettingsProvider.cache } returns null
        every { nugetPackagesGlobalDirObserver.output } returns ""

        // act
        manager.registerAndRestoreCache(context, nugetPackagesGlobalDirObserver)

        // assert
        verify(exactly = 0) { context.newCacheRootUsage(any(), any()) }
        verify(exactly = 0) { _cache.registerAndRestore(any()) }
    }

    @Test
    fun `should update invalidation data`() {
        // arrange
        val context = mockk<DotnetDepCacheBuildStepContext>(relaxed = true)
        val deferredData = mockk<Deferred<Map<String, String>>>()
        coEvery { deferredData.await() } returns invalidationData
        every { context.invalidationData } returns deferredData
        every { context.invalidationDataAwaitTimeout } returns 10000
        every { context.cachesLocations } returns mutableSetOf(cachesDir.toPath())
        val cachesLocationsSlot = slot<Set<Path>>()
        val invalidationDataSlot = slot<Map<String, String>>()
        val manager = create()

        // act
        manager.updateInvalidationData(context)

        // assert
        verify(exactly = 1) { _invalidator.addChecksumsToCachesLocations(capture(cachesLocationsSlot), capture(invalidationDataSlot)) }
        Assert.assertEquals(cachesLocationsSlot.captured, mutableSetOf(cachesDir.toPath()))
        Assert.assertEquals(invalidationDataSlot.captured, invalidationData)
    }

    @Test
    fun `should not update invalidation data when cache is disabled`() {
        // arrange
        val context = mockk<DotnetDepCacheBuildStepContext>(relaxed = true)
        every { _dotnetDepCacheSettingsProvider.cache } returns null
        val manager = create()

        // act
        manager.updateInvalidationData(context)

        // assert
        verify(exactly = 0) { _invalidator.addChecksumsToCachesLocations(any(), any()) }
    }

    @Test
    fun `should prepare invalidation data`() {
        // arrange
        val context = mockk<DotnetDepCacheBuildStepContext>(relaxed = true)
        coEvery { _invalidationDataCollector.collect(workDir, _cache, any()) } returns Result.success(invalidationData)
        val manager = create()

        // act
        manager.prepareInvalidationDataAsync(workDir, context)

        // assert
        verify(exactly = 1) { context.invalidationData = any() }
    }

    private fun create() = DotnetDepCacheManager(
        _loggerService, _dotnetDepCacheSettingsProvider,
        _buildInfo, coroutineScope, _invalidationDataCollector
    )

    private fun cacheRootUsage() = CacheRootUsage(
        DotnetDependencyCacheConstants.CACHE_ROOT_TYPE,
        cachesDir.toPath(),
        stepId
    )
}