package jetbrains.buildServer.dotnet.test.dotnet.commands.transformation

import io.mockk.*
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.TempFiles
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agent.cache.depcache.DependencyCache
import jetbrains.buildServer.agent.runner.BuildStepContext
import jetbrains.buildServer.agent.runner.ParametersService
import jetbrains.buildServer.depcache.DotnetDepCacheBuildStepContext
import jetbrains.buildServer.depcache.DotnetDepCacheBuildStepContextHolder
import jetbrains.buildServer.depcache.DotnetDepCacheManager
import jetbrains.buildServer.dotnet.*
import jetbrains.buildServer.dotnet.commands.NugetLocalsCommand
import jetbrains.buildServer.dotnet.commands.targeting.TargetArguments
import jetbrains.buildServer.dotnet.commands.transformation.DotnetCommandsTransformationStage
import jetbrains.buildServer.dotnet.commands.transformation.DotnetDepCacheRestorationCommandTransformer
import jetbrains.buildServer.dotnet.commands.transformation.DotnetDepCacheRestorationCommandTransformer.Companion.MinDotNetSdkVersionForDepCache
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.io.File

class DotnetDepCacheRestorationCommandTransformerTest {
    @MockK
    private lateinit var _nugetLocalsCommand: NugetLocalsCommand
    @MockK
    private lateinit var _dotnetDepCacheManager: DotnetDepCacheManager
    @MockK
    private lateinit var _buildStepContext: BuildStepContext
    @MockK
    private lateinit var _restorePackagesPathManager: RestorePackagesPathManager
    @MockK
    private lateinit var _virtualContext: VirtualContext
    @MockK
    private lateinit var _buildStepContextHolder: DotnetDepCacheBuildStepContextHolder
    @MockK(relaxed = true)
    private lateinit var _dependencyCache: DependencyCache
    private lateinit var _restorePackagesPathArgumentsProvider: ArgumentsProvider
    private lateinit var tempFiles: TempFiles
    private lateinit var cachesDir: File

    @BeforeMethod
    fun setup(){
        clearAllMocks()
        MockKAnnotations.init(this)
        _restorePackagesPathArgumentsProvider = RestorePackagesPathArgumentsProvider(_restorePackagesPathManager, _buildStepContext)
        tempFiles = TempFiles()
        cachesDir = File(tempFiles.createTempDir(), DotnetRunnerCacheDirectoryProvider.DOTNET_CACHE_DIR)
        every { _nugetLocalsCommand.commandType } returns DotnetCommandType.NuGetLocals
        val runnerContext = mockk<BuildRunnerContext>()
        every { _buildStepContext.runnerContext } returns runnerContext
        val build = mockk<AgentRunningBuild>()
        every { runnerContext.build } returns build
        val buildFeature = mockk<AgentBuildFeature>()
        every { build.getBuildFeaturesOfType(any()) } returns listOf(buildFeature)
        val agentConfiguration = mockk<BuildAgentConfiguration>()
        every { build.agentConfiguration } returns agentConfiguration
        every { _dotnetDepCacheManager.cache } returns _dependencyCache
        every { _dotnetDepCacheManager.cacheEnabled } returns true
        every { _restorePackagesPathManager.shouldOverrideRestorePackagesPath() } returns false
        every { _buildStepContextHolder.context } returns DotnetDepCacheBuildStepContext.newContext(mockk<ParametersService>())
    }

    @Test
    fun `should be on DepCacheRestoration stage`() {
        // arrange
        val transformer = create()

        // act
        val result = transformer.stage

        // assert
        Assert.assertEquals(result, DotnetCommandsTransformationStage.DepCacheRestoration)
    }

    @Test
    fun `should wrap initial commands into auxiliary ones for dependency cache`() {
        // arrange
        val context = mockk<DotnetCommandContext>()
        every { context.toolVersion } returns MinDotNetSdkVersionForDepCache

        val (command1, command2) = Pair(mockk<DotnetCommand>(), mockk<DotnetCommand>())
        every { command1.commandType } returns DotnetCommandType.Build
        every { command1.targetArguments } returns emptySequence()
        every { command2.commandType } returns DotnetCommandType.Test
        every { command2.targetArguments } returns emptySequence()

        val transformer = create()

        // act
        val result = transformer.apply(context, sequenceOf(command1, command2)).toList()

        // assert: there are 2 initial commands + auxiliary one per initial
        Assert.assertEquals(result.size, 4)
        Assert.assertEquals(result.get(0).commandType, DotnetCommandType.NuGetLocals) // NuGetLocals auxiliary command for the initial command 1
        Assert.assertEquals(result.get(1).commandType, command1.commandType) // initial command 1
        Assert.assertEquals(result.get(2).commandType, DotnetCommandType.NuGetLocals) // NuGetLocals auxiliary command for the initial command 2
        Assert.assertEquals(result.get(3).commandType, command2.commandType) // initial command 2
    }

    @DataProvider(name = "incompatibleDotnetCommandTypes")
    fun getIncompatibleDotnetCommandTypes(): Array<Array<Any>> {
        return arrayOf(
            arrayOf(DotnetCommandType.VisualStudio),
            arrayOf(DotnetCommandType.Custom),
            arrayOf(DotnetCommandType.VSTest),
            arrayOf(DotnetCommandType.NuGetPush),
            arrayOf(DotnetCommandType.NuGetDelete)
        )
    }
    @Test(dataProvider = "incompatibleDotnetCommandTypes")
    fun `should return initial command when command is not dep cache compatible`(incompatibleCommandType: DotnetCommandType) {
        // arrange
        val context = mockk<DotnetCommandContext>()
        every { context.toolVersion } returns MinDotNetSdkVersionForDepCache

        val command = mockk<DotnetCommand>()
        every { command.commandType } returns incompatibleCommandType
        every { command.targetArguments } returns emptySequence()

        val transformer = create()

        // act
        val result = transformer.apply(context, sequenceOf(command)).toList()

        // assert: there is only an initial command
        Assert.assertEquals(result.size, 1)
        Assert.assertEquals(result.get(0).commandType, incompatibleCommandType)
    }

    @DataProvider(name = "incompatibleTargets")
    fun getIncompatibleTargets(): Array<Array<Any>> {
        return arrayOf(
            arrayOf("1.exe"),
            arrayOf("prj.dll")
        )
    }
    @Test(dataProvider = "incompatibleTargets")
    fun `should return initial command when target arguments are not dep cache compatible`(incompatibleTarget: String) {
        // arrange
        val context = mockk<DotnetCommandContext>()
        every { context.toolVersion } returns MinDotNetSdkVersionForDepCache

        val command = mockk<DotnetCommand>()
        every { command.commandType } returns DotnetCommandType.Build
        every { command.targetArguments } returns sequenceOf(TargetArguments(sequenceOf(CommandLineArgument(incompatibleTarget))))

        val transformer = create()

        // act
        val result = transformer.apply(context, sequenceOf(command)).toList()

        // assert: there is only an initial command
        Assert.assertEquals(result.size, 1)
        Assert.assertEquals(result.get(0).commandType, DotnetCommandType.Build) // the initial command
    }

    @Test
    fun `should return initial commands when dotnet dependency cache feature is disabled`() {
        // arrange
        every { _dotnetDepCacheManager.cache } returns null
        every { _dotnetDepCacheManager.cacheEnabled } returns false
        val context = mockk<DotnetCommandContext>()
        every { context.toolVersion } returns MinDotNetSdkVersionForDepCache
        val (command1, command2) = Pair(mockk<DotnetCommand>(), mockk<DotnetCommand>())
        val transformer = create()

        // act
        val result = transformer.apply(context, sequenceOf(command1, command2)).toList()

        // assert
        Assert.assertEquals(result.size, 2)
        Assert.assertEquals(result.get(0), command1) // initial command 1
        Assert.assertEquals(result.get(1), command2) // initial command 2
    }

    @Test
    fun `should return initial commands when tool version is less that minimal required`() {
        // arrange
        val context = mockk<DotnetCommandContext>()
        every { context.toolVersion } returns Version(2, 1, 105)
        val (command1, command2) = Pair(mockk<DotnetCommand>(), mockk<DotnetCommand>())
        val transformer = create()

        // act
        val result = transformer.apply(context, sequenceOf(command1, command2)).toList()

        // assert
        Assert.assertEquals(result.size, 2)
        Assert.assertEquals(result.get(0), command1) // initial command 1
        Assert.assertEquals(result.get(1), command2) // initial command 2
    }

    @Test
    fun `should override RestorePackagesPath when context is virtual`() {
        // arrange
        val context = mockk<DotnetCommandContext>()
        every { context.toolVersion } returns MinDotNetSdkVersionForDepCache
        every { _virtualContext.isVirtual } returns true
        every { _restorePackagesPathManager.shouldOverrideRestorePackagesPath() } returns true
        every { _restorePackagesPathManager.getRestorePackagesPathLocation(any()) } returns cachesDir
        every { _dotnetDepCacheManager.registerAndRestoreCache(any(), any<File>()) } just Runs

        val (command1, command2) = Pair(mockk<DotnetCommand>(), mockk<DotnetCommand>())
        every { command1.commandType } returns DotnetCommandType.Build
        every { command1.targetArguments } returns emptySequence()
        every { command1.getArguments(context) } returns emptySequence() // command1 has no arguments
        every { command2.commandType } returns DotnetCommandType.Test
        every { command2.targetArguments } returns emptySequence()
        val command2Args = listOf(
            CommandLineArgument("--some-command-line-argument"),
            CommandLineArgument("-p:RestorePackagesPath=CustomPath")
        )
        every { command2.getArguments(context) } returns command2Args.asSequence()  // command1 has 2 arguments

        val transformer = create()

        // act
        val result = transformer.apply(context, sequenceOf(command1, command2)).toList()

        // assert
        // there are 2 initial commands
        // NuGetLocals wasn't executed, since the cache root location is known
        Assert.assertEquals(result.size, 2)
        Assert.assertEquals(result.get(0).commandType, command1.commandType) // initial command 1
        Assert.assertEquals(result.get(1).commandType, command2.commandType) // initial command 2

        // there were 2 invocations of the registerAndRestoreCache per each initial command
        verify(exactly = 2) { _dotnetDepCacheManager.registerAndRestoreCache(any(), cachesDir) }

        // RestorePackagesPath was overridden for the command1
        val expectedRestorePackagesPath = "-p:RestorePackagesPath=${cachesDir.absolutePath}"
        val command1ArgsResult = result.get(0).getArguments(context).toList()
        Assert.assertEquals(command1ArgsResult.size, 1) // initially, 0
        Assert.assertEquals(command1ArgsResult.get(0).value, expectedRestorePackagesPath)

        // RestorePackagesPath was overridden for the command1
        val command2ArgsResult = result.get(1).getArguments(context).toList()
        Assert.assertEquals(command2ArgsResult.size, 3) // // initially, 2
        Assert.assertEquals(command2ArgsResult.get(2).value, expectedRestorePackagesPath)
    }

    @Test
    fun `should not override RestorePackagesPath when context is not virtual`() {
        // arrange
        val context = mockk<DotnetCommandContext>()
        every { context.toolVersion } returns MinDotNetSdkVersionForDepCache
        every { _virtualContext.isVirtual } returns false
        every { _restorePackagesPathManager.shouldOverrideRestorePackagesPath() } returns true
        every { _restorePackagesPathManager.getRestorePackagesPathLocation(any()) } returns cachesDir
        every { _dotnetDepCacheManager.registerAndRestoreCache(any(), any<File>()) } just Runs

        val (command1, command2) = Pair(mockk<DotnetCommand>(), mockk<DotnetCommand>())
        every { command1.commandType } returns DotnetCommandType.Build
        every { command1.targetArguments } returns emptySequence()
        every { command1.getArguments(context) } returns emptySequence() // command1 has no arguments
        every { command2.commandType } returns DotnetCommandType.Test
        every { command2.targetArguments } returns emptySequence()
        val command2Args = listOf(
            CommandLineArgument("--some-command-line-argument"),
            CommandLineArgument("-p:RestorePackagesPath=CustomPath")
        )
        every { command2.getArguments(context) } returns command2Args.asSequence()  // command1 has 2 arguments

        val transformer = create()

        // act
        val result = transformer.apply(context, sequenceOf(command1, command2)).toList()

        // assert
        // there are 2 initial commands + auxiliary one per initial
        // NuGetLocals was executed since the cache root location is unknown
        Assert.assertEquals(result.size, 4)
        Assert.assertEquals(result.get(0).commandType, DotnetCommandType.NuGetLocals) // NuGetLocals auxiliary command for the initial command 1
        Assert.assertEquals(result.get(1).commandType, command1.commandType) // initial command 1
        Assert.assertEquals(result.get(2).commandType, DotnetCommandType.NuGetLocals) // NuGetLocals auxiliary command for the initial command 2
        Assert.assertEquals(result.get(3).commandType, command2.commandType) // initial command 2

        // RestorePackagesPath wasn't overridden for the initial commands
        val command1ArgsResult = result.get(1).getArguments(context).toList()
        Assert.assertEquals(command1ArgsResult.size, 0) // initially, 0
        Assert.assertEquals(command1ArgsResult, command1.getArguments(context).toList())

        val command2ArgsResult = result.get(3).getArguments(context).toList()
        Assert.assertEquals(command2ArgsResult.size, 2) // // initially, 2
        Assert.assertEquals(command2ArgsResult, command2Args)
    }

    private fun create() = DotnetDepCacheRestorationCommandTransformer(
        _nugetLocalsCommand, _dotnetDepCacheManager, _restorePackagesPathArgumentsProvider,
        _restorePackagesPathManager, _buildStepContext, _virtualContext, _buildStepContextHolder
    )
}