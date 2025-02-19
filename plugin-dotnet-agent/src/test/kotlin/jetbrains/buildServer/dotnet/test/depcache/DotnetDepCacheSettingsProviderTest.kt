package jetbrains.buildServer.dotnet.test.depcache

import io.mockk.*
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agent.cache.depcache.DependencyCacheProvider
import jetbrains.buildServer.agent.cache.depcache.DependencyCacheSettings
import jetbrains.buildServer.agent.cache.depcache.DependencyCacheSettingsProviderRegistry
import jetbrains.buildServer.cache.depcache.DependencyCacheConstants.*
import jetbrains.buildServer.depcache.DotnetDepCacheChecksumBuilder
import jetbrains.buildServer.depcache.DotnetDepCachePackagesChangedInvalidator
import jetbrains.buildServer.depcache.DotnetDepCacheSettingsProvider
import jetbrains.buildServer.depcache.DotnetDependencyCacheConstants.FEATURE_TOGGLE_DOTNET_DEPENDENCY_CACHE
import jetbrains.buildServer.dotnet.DotnetConstants.RUNNER_TYPE
import jetbrains.buildServer.util.EventDispatcher
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

class DotnetDepCacheSettingsProviderTest {

    @MockK private lateinit var eventDispatcherMock: EventDispatcher<AgentLifeCycleListener>
    @MockK private lateinit var cacheSettingsProviderRegistryMock: DependencyCacheSettingsProviderRegistry
    @MockK private lateinit var buildMock: AgentRunningBuild
    @MockK private lateinit var checksumBuilder: DotnetDepCacheChecksumBuilder
    private lateinit var sharedBuildConfig: MutableMap<String, String?>
    private lateinit var instance: DotnetDepCacheSettingsProvider

    @BeforeMethod
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        clearAllMocks()
        sharedBuildConfig = mutableMapOf(
            FEATURE_TOGGLE_DOTNET_DEPENDENCY_CACHE to "true",
            EPHEMERAL_AGENT_PARAMETER to "false"
        )
        every { buildMock.getSharedConfigParameters() } returns sharedBuildConfig

        instance = DotnetDepCacheSettingsProvider(
            eventDispatcherMock, cacheSettingsProviderRegistryMock, mockk<DependencyCacheProvider>(), checksumBuilder
        )
    }

    @Test
    fun `should return settings and create invalidator when build feature exists`() {
        // arrange
        val buildFeatureMock = mockk<AgentBuildFeature>()
        every { buildFeatureMock.type } returns BUILD_FEATURE_TYPE
        every { buildMock.getBuildFeaturesOfType(any()) } returns listOf(buildFeatureMock)
        val buildRunnerMock = mockk<BuildRunnerSettings>()
        every { buildRunnerMock.isEnabled } returns true
        every { buildRunnerMock.runType } returns RUNNER_TYPE
        every { buildMock.buildRunners } returns listOf(buildRunnerMock)

        // act
        val cacheSettings: List<DependencyCacheSettings> = instance.getSettings(buildMock)
        val invalidator: DotnetDepCachePackagesChangedInvalidator? = instance.postBuildInvalidator

        // assert
        Assert.assertFalse(cacheSettings.isEmpty())
        Assert.assertEquals(cacheSettings.size, 1)
        Assert.assertNotNull(invalidator)
    }

    @Test
    fun `should return settings and create invalidator when enable all runners parameter is true`() {
        // arrange
        sharedBuildConfig[DEPENDENCY_CACHE_ENABLE_ALL_RUNNERS_PARAM] = "true"
        every { buildMock.getBuildFeaturesOfType(any()) } returns emptyList()
        val buildRunnerMock = mockk<BuildRunnerSettings>()
        every { buildRunnerMock.isEnabled } returns true
        every { buildRunnerMock.runType } returns RUNNER_TYPE
        every { buildMock.buildRunners } returns listOf(buildRunnerMock)

        // act
        val cacheSettings: List<DependencyCacheSettings> = instance.getSettings(buildMock)
        val invalidator: DotnetDepCachePackagesChangedInvalidator? = instance.postBuildInvalidator

        // assert
        Assert.assertFalse(cacheSettings.isEmpty())
        Assert.assertEquals(cacheSettings.size, 1)
        Assert.assertNotNull(invalidator)
    }

    @Test
    fun `should not return settings and create invalidator when feature toggle is disabled`() {
        // arrange
        val buildFeatureMock = mockk<AgentBuildFeature>()
        every { buildFeatureMock.type } returns BUILD_FEATURE_TYPE
        every { buildMock.getBuildFeaturesOfType(any()) } returns listOf(buildFeatureMock)
        sharedBuildConfig[DEPENDENCY_CACHE_ENABLE_ALL_RUNNERS_PARAM] = "true"
        sharedBuildConfig[FEATURE_TOGGLE_DOTNET_DEPENDENCY_CACHE] = "false"
        val buildRunnerMock = mockk<BuildRunnerSettings>()
        every { buildRunnerMock.isEnabled } returns true
        every { buildRunnerMock.runType } returns RUNNER_TYPE
        every { buildMock.buildRunners } returns listOf(buildRunnerMock)

        // act
        val cacheSettings: List<DependencyCacheSettings?> = instance.getSettings(buildMock)
        val invalidator: DotnetDepCachePackagesChangedInvalidator? = instance.postBuildInvalidator

        // assert
        Assert.assertTrue(cacheSettings.isEmpty())
        Assert.assertNull(invalidator)
    }

    @Test
    fun `should not return settings and create invalidator when agent is not ephemeral and restriction is enabled`() {
        // arrange
        val buildFeatureMock = mockk<AgentBuildFeature>()
        every { buildFeatureMock.type } returns BUILD_FEATURE_TYPE
        every { buildMock.getBuildFeaturesOfType(any()) } returns listOf(buildFeatureMock)
        sharedBuildConfig[DEPENDENCY_CACHE_ENABLE_ALL_RUNNERS_PARAM] = "true"
        sharedBuildConfig[DEPENDENCY_CACHE_EPHEMERAL_AGENTS_ONLY] = "true"
        sharedBuildConfig[EPHEMERAL_AGENT_PARAMETER] = "false"
        val buildRunnerMock = mockk<BuildRunnerSettings>()
        every { buildRunnerMock.isEnabled } returns true
        every { buildRunnerMock.runType } returns RUNNER_TYPE
        every { buildMock.buildRunners } returns listOf(buildRunnerMock)

        // act
        val cacheSettings: List<DependencyCacheSettings?> = instance.getSettings(buildMock)
        val invalidator: DotnetDepCachePackagesChangedInvalidator? = instance.postBuildInvalidator

        // assert
        Assert.assertTrue(cacheSettings.isEmpty())
        Assert.assertNull(invalidator)
    }

    @Test
    fun `should not return settings and create invalidator when there are no dotnet steps`() {
        // arrange
        val buildFeatureMock = mockk<AgentBuildFeature>()
        every { buildFeatureMock.type } returns BUILD_FEATURE_TYPE
        every { buildMock.getBuildFeaturesOfType(any()) } returns listOf(buildFeatureMock)
        sharedBuildConfig[DEPENDENCY_CACHE_ENABLE_ALL_RUNNERS_PARAM] = "true"
        val buildRunnerMock = mockk<BuildRunnerSettings>()
        every { buildRunnerMock.isEnabled } returns true
        every { buildRunnerMock.runType } returns "inaproppriateRunner"
        every { buildMock.buildRunners } returns listOf(buildRunnerMock)

        // act
        val cacheSettings: List<DependencyCacheSettings?> = instance.getSettings(buildMock)
        val invalidator: DotnetDepCachePackagesChangedInvalidator? = instance.postBuildInvalidator

        // assert
        Assert.assertTrue(cacheSettings.isEmpty())
        Assert.assertNull(invalidator)
    }

    @Test
    fun `should not return settings and create invalidator when there are no enabled dotnet steps`() {
        // arrange
        val buildFeatureMock = mockk<AgentBuildFeature>()
        every { buildFeatureMock.type } returns BUILD_FEATURE_TYPE
        every { buildMock.getBuildFeaturesOfType(any()) } returns listOf(buildFeatureMock)
        sharedBuildConfig[DEPENDENCY_CACHE_ENABLE_ALL_RUNNERS_PARAM] = "true"
        val buildRunnerMock = mockk<BuildRunnerSettings>()
        every { buildRunnerMock.isEnabled } returns false
        every { buildRunnerMock.runType } returns RUNNER_TYPE
        every { buildMock.buildRunners } returns listOf(buildRunnerMock)

        // act
        val cacheSettings: List<DependencyCacheSettings?> = instance.getSettings(buildMock)
        val invalidator: DotnetDepCachePackagesChangedInvalidator? = instance.postBuildInvalidator

        // assert
        Assert.assertTrue(cacheSettings.isEmpty())
        Assert.assertNull(invalidator)
    }

    @DataProvider
    fun getEnableAllRunnersValues(): Array<Array<String?>> = arrayOf(
        arrayOf("false"),
        arrayOf("abcd"),
        arrayOf<String?>(null)
    )

    @Test(dataProvider = "getEnableAllRunnersValues")
    fun `should not return settings and create invalidator when there is no build feature and enable all runners parameter is not true`(paramValue: String?) {
        // arrange
        every { buildMock.getBuildFeaturesOfType(any()) } returns emptyList()
        sharedBuildConfig[DEPENDENCY_CACHE_ENABLE_ALL_RUNNERS_PARAM] = paramValue

        // act
        val cacheSettings: List<DependencyCacheSettings?> = instance.getSettings(buildMock)
        val invalidator: DotnetDepCachePackagesChangedInvalidator? = instance.postBuildInvalidator

        // assert
        Assert.assertTrue(cacheSettings.isEmpty())
        Assert.assertNull(invalidator)
    }

    @Test
    fun `should unset invalidator when build finished`() {
        // arrange
        val buildFeatureMock = mockk<AgentBuildFeature>()
        every { buildFeatureMock.type } returns BUILD_FEATURE_TYPE
        every { buildMock.getBuildFeaturesOfType(any()) } returns listOf(buildFeatureMock)
        val buildRunnerMock = mockk<BuildRunnerSettings>()
        every { buildRunnerMock.isEnabled } returns true
        every { buildRunnerMock.runType } returns RUNNER_TYPE
        every { buildMock.buildRunners } returns listOf(buildRunnerMock)

        // act
        instance.register()
        val cacheSettings: List<DependencyCacheSettings?> = instance.getSettings(buildMock)
        val invalidator: DotnetDepCachePackagesChangedInvalidator? = instance.postBuildInvalidator
        val listenerSlot = slot<AgentLifeCycleListener>()

        verify { eventDispatcherMock.addListener(capture(listenerSlot)) }
        val actualListener = listenerSlot.captured
        Assert.assertNotNull(actualListener)
        actualListener.buildFinished(buildMock, BuildFinishedStatus.FINISHED_SUCCESS)
        val invalidatorAfterBuildFinished: DotnetDepCachePackagesChangedInvalidator? = instance.postBuildInvalidator

        // assert
        Assert.assertNotNull(cacheSettings)
        Assert.assertNotNull(invalidator)
        Assert.assertNull(invalidatorAfterBuildFinished)
    }

    private companion object {
        val BUILD_FEATURE_TYPE = DEPENDENCY_CACHE_BUILD_FEATURE_TYPE_PREFIX + "." + RUNNER_TYPE;
    }
}