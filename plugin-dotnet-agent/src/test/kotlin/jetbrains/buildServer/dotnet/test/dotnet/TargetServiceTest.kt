package jetbrains.buildServer.dotnet.test.dotnet

import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.RunBuildException
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agent.runner.ParameterType
import jetbrains.buildServer.agent.runner.ParametersService
import jetbrains.buildServer.agent.runner.PathType
import jetbrains.buildServer.agent.runner.PathsService
import jetbrains.buildServer.dotnet.CommandTarget
import jetbrains.buildServer.dotnet.DotnetConstants
import jetbrains.buildServer.dotnet.TargetService
import jetbrains.buildServer.dotnet.TargetServiceImpl
import jetbrains.buildServer.util.OSType
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.io.File

class TargetServiceTest {
    @MockK private lateinit var _pathsService: PathsService
    @MockK private lateinit var _parametersService: ParametersService
    @MockK private lateinit var _argumentsService: ArgumentsService
    @MockK private lateinit var _pathMatcher: PathMatcher
    @MockK private lateinit var _fileSystemService: FileSystemService
    @MockK private lateinit var _virtualContext: VirtualContext

    @BeforeMethod
    fun setUp() {
        MockKAnnotations.init(this)
        clearAllMocks()
    }

    @Test
    fun shouldProvideTargets() {
        // Given
        val instance = createInstance()
        val checkoutDirectory = File("checkout")
        val includeRules = sequenceOf("rule1", "rule2", "rule3")

        // When
        every { _parametersService.tryGetParameter(ParameterType.Runner, DotnetConstants.PARAM_PATHS) } returns "some includeRules"
        every { _pathsService.getPath(PathType.WorkingDirectory) } returns checkoutDirectory
        every { _argumentsService.split("some includeRules") } returns includeRules
        every { _virtualContext.targetOSType } returns OSType.UNIX
        every { _fileSystemService.isAbsolute(any()) } returns false
        every { _virtualContext.resolvePath(any()) } answers { "v_" + File(arg<String>(0)).name }

        val actualTargets = instance.targets.toList()

        // Then
        Assert.assertEquals(actualTargets, includeRules.map { CommandTarget(Path("v_${File(it).name}")) }.toList())
    }

    @Test
    fun shouldNotChangeTargetsWhenWindows() {
        // Given
        val instance = createInstance()
        val checkoutDirectory = File("checkout")
        val includeRules = sequenceOf("rule1", "rule2", "rule3")

        // When
        every { _parametersService.tryGetParameter(ParameterType.Runner, DotnetConstants.PARAM_PATHS) } returns "some includeRules"
        every { _pathsService.getPath(PathType.WorkingDirectory) } returns checkoutDirectory
        every { _argumentsService.split("some includeRules") } returns includeRules
        every { _virtualContext.targetOSType } returns OSType.WINDOWS

        val actualTargets = instance.targets.toList()

        // Then
        Assert.assertEquals(actualTargets, includeRules.map { CommandTarget(Path(it)) }.toList())
    }

    @Test
    fun shouldExecuteMatcherForWildcards() {
        // Given
        val instance = createInstance()
        val checkoutDirectory = File("checkout")
        val includeRules = sequenceOf("rule1", "rule/**/2", "rul?3")
        val expectedRules = sequenceOf("rule1", "rule/a/2", "rule/b/c/2", "rule3")

        // When
        every { _parametersService.tryGetParameter(ParameterType.Runner, DotnetConstants.PARAM_PATHS) } returns "some includeRules"
        every { _pathsService.getPath(PathType.WorkingDirectory) } returns checkoutDirectory
        every { _argumentsService.split("some includeRules") } returns includeRules
        every { _pathMatcher.match(checkoutDirectory, listOf("rule/**/2")) } returns listOf(File("rule/a/2"), File("rule/b/c/2"))
        every { _pathMatcher.match(checkoutDirectory, listOf("rul?3")) } returns listOf(File("rule3"))
        every { _fileSystemService.isAbsolute(any()) } returns false
        every { _virtualContext.targetOSType } returns OSType.UNIX
        every { _virtualContext.resolvePath(any()) } answers { "v_" + File(arg<String>(0)).name }

        val actualTargets = instance.targets.toList()

        // Then
        Assert.assertEquals(actualTargets, expectedRules.map { CommandTarget(Path("v_${File(it).name}")) }.toList())
    }

    @Test
    fun shouldThrowRunBuildExceptionWhenTargetsWereNotMatched() {
        // Given
        val instance = createInstance()
        val checkoutDirectory = File("checkout")
        val includeRules = listOf("rule1", "rule/**/2", "rule3")

        // When
        every { _parametersService.tryGetParameter(ParameterType.Runner, DotnetConstants.PARAM_PATHS) } returns "some includeRules"
        every { _pathsService.getPath(PathType.WorkingDirectory) } returns checkoutDirectory
        every { _argumentsService.split("some includeRules") } returns includeRules.asSequence()
        every { _pathMatcher.match(checkoutDirectory, listOf("rule/**/2")) } returns emptyList<File>()
        every { _fileSystemService.isAbsolute(any()) } returns false
        every { _virtualContext.targetOSType } returns OSType.MAC
        every { _virtualContext.resolvePath(any()) } answers { "v_" + File(arg<String>(0)).name }

        var actualExceptionWasThrown = false
        try {
            instance.targets.toList()
        } catch (ex: RunBuildException) {
            actualExceptionWasThrown = true
        }

        // Then
        Assert.assertEquals(actualExceptionWasThrown, true)
    }

    @DataProvider
    fun emptyPathsParam(): Array<Array<out String?>> {
        return arrayOf(
                arrayOf(""),
                arrayOf("  "),
                arrayOf(null as String?))
    }

    @Test(dataProvider = "emptyPathsParam")
    fun shouldProvideEmptyTargetsSequenceWhenPathsParamIsEmpty(pathsParam: String?) {
        // Given
        val instance = createInstance()
        val checkoutDirectory = File("checkout")

        // When
        every { _parametersService.tryGetParameter(ParameterType.Runner, DotnetConstants.PARAM_PATHS) } returns pathsParam
        every { _pathsService.getPath(PathType.WorkingDirectory) } returns checkoutDirectory

        val actualTargets = instance.targets.toList()

        // Then
        Assert.assertEquals(actualTargets, emptyList<CommandTarget>())
    }

    private fun createInstance(): TargetService {
        return TargetServiceImpl(
                _pathsService,
                _parametersService,
                _argumentsService,
                _pathMatcher,
                _fileSystemService,
                _virtualContext)
    }
}