package jetbrains.buildServer.dotnet.test.depcache

import io.mockk.mockk
import jetbrains.buildServer.TempFiles
import jetbrains.buildServer.agent.cache.depcache.DependencyCache
import jetbrains.buildServer.depcache.DotnetDepCacheChecksumBuilder
import jetbrains.buildServer.util.FileUtil
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File

class DotnetDepCacheChecksumBuilderTest {

    private lateinit var collector: DotnetDepCacheChecksumBuilder
    private lateinit var tempFiles: TempFiles
    private lateinit var testDir: File

    @BeforeMethod
    fun beforeTest() {
        collector = DotnetDepCacheChecksumBuilder()
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
        // consists of:
        // /nuget.config
        // /SubPrj1/SubPrj1.csproj
        // /SubPrj1/Directory.Build.props
        // /SubPrj2/SubPrj2.csproj
        // /SubPrj2/Directory.Build.targets
        val expected = "fb9be09d0dac2f2d830e0e928713376ce330262dd08e88aa8d904d4a7e4fc63f"

        // act
        val result = collector.build(testDir, depCache, Integer.MAX_VALUE)

        // assert
        Assert.assertFalse(result.isFailure)
        val checksum = result.getOrThrow()
        Assert.assertEquals(checksum, expected)

        // act: checking for the second time
        val result2 = collector.build(testDir, depCache, Integer.MAX_VALUE)

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
        // /nuget.config from the root of the project
        val expected = "b44ea422159fabdab205e0a023859a3e153b1eca876edae116acf16d91700d25"

        // act
        val result = collector.build(testDir, depCache, depthLimit)

        // assert
        Assert.assertFalse(result.isFailure)
        val checksum = result.getOrThrow()
        Assert.assertEquals(checksum, expected)
    }
}