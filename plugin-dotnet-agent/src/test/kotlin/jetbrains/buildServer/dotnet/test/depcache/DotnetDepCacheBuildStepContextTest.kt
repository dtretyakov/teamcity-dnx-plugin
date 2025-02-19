package jetbrains.buildServer.dotnet.test.depcache

import io.mockk.mockk
import jetbrains.buildServer.agent.runner.ParametersService
import jetbrains.buildServer.depcache.DotnetDepCacheBuildStepContext
import org.testng.Assert
import org.testng.annotations.Test
import java.nio.file.Paths

class DotnetDepCacheBuildStepContextTest {

    @Test
    fun `should increment execution number` () {
        // arrange
        val parametersService = mockk<ParametersService>()
        val context = DotnetDepCacheBuildStepContext(parametersService)
        var commandsCounter = 0

        // act
        var cacheRootUsage = context.newCacheRootUsage(Paths.get("path"), "stepId")

        // assert
        Assert.assertEquals(cacheRootUsage.descriptor, "stepId.$commandsCounter")

        // act
        commandsCounter++
        cacheRootUsage = context.newCacheRootUsage(Paths.get("path"), "stepId")

        // assert
        Assert.assertEquals(cacheRootUsage.descriptor, "stepId.$commandsCounter")
    }

}