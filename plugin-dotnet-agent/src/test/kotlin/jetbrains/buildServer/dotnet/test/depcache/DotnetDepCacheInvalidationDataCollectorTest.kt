package jetbrains.buildServer.dotnet.test.depcache

import io.mockk.mockk
import jetbrains.buildServer.TempFiles
import jetbrains.buildServer.agent.cache.depcache.DependencyCache
import jetbrains.buildServer.depcache.DotnetDepCacheInvalidationDataCollector
import jetbrains.buildServer.util.FileUtil
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File

class DotnetDepCacheInvalidationDataCollectorTest {

    private lateinit var collector: DotnetDepCacheInvalidationDataCollector
    private lateinit var tempFiles: TempFiles
    private lateinit var testDir: File

    @BeforeMethod
    fun beforeTest() {
        collector = DotnetDepCacheInvalidationDataCollector()
        tempFiles = TempFiles()
        testDir = tempFiles.createTempDir()
    }

    @Test
    fun `should collect invalidation data`() {
        // arrange
        val projectName = "jetbrains/buildServer/dotnet/test/depcache/MultiModuleProject"
        val projectDir = File(this::class.java.classLoader.getResource(projectName)!!.toURI())
        FileUtil.copyDir(projectDir, testDir, true)
        val depCache = mockk<DependencyCache>(relaxed = true)

        // act
        val result = collector.collect(testDir, depCache, Integer.MAX_VALUE)

        // assert
        Assert.assertFalse(result.isFailure)
        val data = result.getOrThrow()
        Assert.assertEquals(data.size, 5)
        Assert.assertNotNull(data["/nuget.config"])
        Assert.assertNotNull(data["/SubPrj1/SubPrj1.csproj"])
        Assert.assertNotNull(data["/SubPrj1/Directory.Build.props"])
        Assert.assertNotNull(data["/SubPrj2/SubPrj2.csproj"])
        Assert.assertNotNull(data["/SubPrj2/Directory.Build.targets"])

        // act: checking for the second time
        val result2 = collector.collect(testDir, depCache, Integer.MAX_VALUE)

        // assert: the result is consistent
        Assert.assertEquals(result2, result)
    }

    @Test
    fun `should respect depth limit when searching for invalidation data`() {
        // arrange
        val projectName = "jetbrains/buildServer/dotnet/test/depcache/MultiModuleProject"
        val projectDir = File(this::class.java.classLoader.getResource(projectName)!!.toURI())
        FileUtil.copyDir(projectDir, testDir, true)
        val depCache = mockk<DependencyCache>(relaxed = true)
        val depthLimit = 0

        // act
        val result = collector.collect(testDir, depCache, depthLimit)

        // assert
        Assert.assertFalse(result.isFailure)
        val data = result.getOrThrow()
        Assert.assertEquals(data.size, 1)
        Assert.assertNotNull(data["/nuget.config"])
    }
}