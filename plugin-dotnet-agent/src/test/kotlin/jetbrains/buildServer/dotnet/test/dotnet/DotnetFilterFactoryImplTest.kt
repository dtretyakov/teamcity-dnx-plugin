

package jetbrains.buildServer.dotnet.test.dotnet

import io.mockk.*
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.agent.Version
import jetbrains.buildServer.dotnet.*
import jetbrains.buildServer.dotnet.commands.test.TestRunSettingsFileProvider
import jetbrains.buildServer.dotnet.commands.test.TestsFilterProvider
import jetbrains.buildServer.dotnet.commands.test.splitting.TestsSplittingMode
import jetbrains.buildServer.dotnet.commands.test.splitting.TestsSplittingModeProvider
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File

class DotnetFilterFactoryImplTest {
    @MockK private lateinit var _testsFilterProvider: TestsFilterProvider
    @MockK private lateinit var _testsSplittingModeProvider: TestsSplittingModeProvider
    @MockK private lateinit var _testRunSettingsFileProvider: TestRunSettingsFileProvider
    @MockK private lateinit var _dotnetCommandContext: DotnetCommandContext

    @BeforeMethod
    fun setUp() {
        MockKAnnotations.init(this)
        clearAllMocks()
    }

    @Test
    public fun `should create filter using settings file when filter size bigger than limit`() {
        // Given
        val factory = createInstance()
        val filter = "a".repeat(DotnetFilterFactoryImpl.MaxArgSize + 1)
        val settingsFile = File("My.settings")

        // When
        every { _testsFilterProvider.getFilterExpression(any()) } returns filter
        every { _testsSplittingModeProvider.getMode(any()) } returns TestsSplittingMode.TestClassNameFilter
        every { _testRunSettingsFileProvider.tryGet(_dotnetCommandContext) } returns settingsFile
        every { _dotnetCommandContext.command.commandType } returns DotnetCommandType.Test
        every { _dotnetCommandContext.toolVersion } returns Version.Empty
        val actualFilter = factory.createFilter(_dotnetCommandContext)

        // Then
        Assert.assertEquals(actualFilter, DotnetFilter("", settingsFile, true))
    }

    @Test
    public fun `should use filter as argument when cannot generate settings file`() {
        // Given
        val factory = createInstance()
        val filter = "a".repeat(DotnetFilterFactoryImpl.MaxArgSize + 1)

        // When
        every { _testsFilterProvider.getFilterExpression(any()) } returns filter
        every { _testsSplittingModeProvider.getMode(any()) } returns TestsSplittingMode.TestClassNameFilter
        every { _testRunSettingsFileProvider.tryGet(_dotnetCommandContext) } returns null
        every { _dotnetCommandContext.command.commandType } returns DotnetCommandType.Test
        every { _dotnetCommandContext.toolVersion } returns Version.Empty
        val actualFilter = factory.createFilter(_dotnetCommandContext)

        // Then
        Assert.assertEquals(actualFilter, DotnetFilter(filter, null, true))
    }

    @Test
    public fun `should use filter as argument when filter is less or eq the limit`() {
        // Given
        val factory = createInstance()
        val filter = "a".repeat(DotnetFilterFactoryImpl.MaxArgSize)

        // When
        every { _testsFilterProvider.getFilterExpression(any()) } returns filter
        every { _testsSplittingModeProvider.getMode(any()) } returns TestsSplittingMode.TestClassNameFilter
        every { _dotnetCommandContext.command.commandType } returns DotnetCommandType.Test
        every { _dotnetCommandContext.toolVersion } returns Version.Empty
        val actualFilter = factory.createFilter(_dotnetCommandContext)

        // Then
        Assert.assertEquals(actualFilter, DotnetFilter(filter, null, true))
        verify(exactly = 0) { _testRunSettingsFileProvider.tryGet(any()) }
    }

    @Test
    public fun `should use filter as argument when no test splitting`() {
        // Given
        val factory = createInstance()
        val filter = "a".repeat(DotnetFilterFactoryImpl.MaxArgSize + 1)

        // When
        every { _testsFilterProvider.getFilterExpression(any()) } returns filter
        every { _testsSplittingModeProvider.getMode(any()) } returns TestsSplittingMode.Disabled
        every { _dotnetCommandContext.command.commandType } returns DotnetCommandType.Test
        every { _dotnetCommandContext.toolVersion } returns Version.Empty
        val actualFilter = factory.createFilter(_dotnetCommandContext)

        // Then
        Assert.assertEquals(actualFilter, DotnetFilter(filter, null, false))
        verify(exactly = 0) { _testRunSettingsFileProvider.tryGet(any()) }
    }

    private fun createInstance() = DotnetFilterFactoryImpl(_testsFilterProvider, _testsSplittingModeProvider, _testRunSettingsFileProvider)
}