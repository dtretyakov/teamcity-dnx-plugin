using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Domain.Suppression;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Domain.TestSelectors;

namespace TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.UnitTests.Domain.Suppression;

using Xunit;
using Moq;
using System.Collections.Generic;

public class TestSuppressionDeciderTests
{
    private readonly ITestSuppressionDecider _decider;
    private readonly Dictionary<string, ITestSelector> _testSelectors;

    public TestSuppressionDeciderTests()
    {
        _decider = new TestSuppressionDecider();
        _testSelectors = new Dictionary<string, ITestSelector>();
    }

    [Fact]
    public void Decide_ShouldThrowArgumentException_WhenTestSelectorQueryIsEmpty()
    {
        Assert.Throws<ArgumentException>(() => _decider.Decide("", false, _testSelectors));
    }

    [Fact]
    public void Decide_ShouldThrowArgumentException_WhenTestSelectorQueryIsNull()
    {
        Assert.Throws<ArgumentException>(() => _decider.Decide(null!, false, _testSelectors));
    }

    [Theory]
    [InlineData("Namespace0.Namespace1.ClassName", true)]
    [InlineData("Namespace0.ClassName", true)]
    [InlineData("ClassName", true)]
    [InlineData("Namespace0.Namespace1.ClassName(str1, str2)", true)]
    [InlineData("Namespace0.ClassName(str1, str2)", true)]
    [InlineData("ClassName(str1, str2)", true)]
    public void Decide_ShouldReturnSuppressedAndNewSelector_WhenTestSelectorDoesNotExistAndInclusionModeIsTrue(string query, bool inclusionMode)
    {
        // arrange, act
        var (shouldBeSuppressed, returnedTestSelector) = _decider.Decide(query, inclusionMode, _testSelectors);

        // assert
        Assert.True(shouldBeSuppressed);
        Assert.IsType<TestClassSelector>(returnedTestSelector);
    }
    
    [Theory]
    [InlineData("Namespace0.Namespace1.ClassName", false)]
    [InlineData("Namespace0.ClassName", false)]
    [InlineData("ClassName", false)]
    [InlineData("Namespace0.Namespace1.ClassName(str1, str2)", false)]
    [InlineData("Namespace0.ClassName(str1, str2)", false)]
    [InlineData("ClassName(str1, str2)", false)]
    public void Decide_ShouldReturnNotSuppressedAndNewSelector_WhenTestSelectorDoesNotExistAndInclusionModeIsTrue(string query, bool inclusionMode)
    {
        // arrange, act
        var (shouldBeSuppressed, returnedTestSelector) = _decider.Decide(query, inclusionMode, _testSelectors);

        // assert
        Assert.False(shouldBeSuppressed);
        Assert.IsType<TestClassSelector>(returnedTestSelector);
    }

    [Theory]
    [InlineData("Namespace0.Namespace1.ClassName")]
    [InlineData("Namespace0.ClassName")]
    [InlineData("ClassName")]
    [InlineData("Namespace0.Namespace1.ClassName(str1, str2)")]
    [InlineData("Namespace0.ClassName(str1, str2)")]
    [InlineData("ClassName(str1, str2)")]
    public void Decide_ShouldReturnNotSuppressedAndExistingSelector_WhenTestSelectorExists(string query)
    {
        // arrange
        var testSelector = Mock.Of<ITestSelector>();
        _testSelectors.Add(query, testSelector);

        // act
        var (shouldBeSuppressed, returnedTestSelector) = _decider.Decide(query, true, _testSelectors);

        // assert
        Assert.False(shouldBeSuppressed);
        Assert.Equal(testSelector, returnedTestSelector);
    }
    
    [Theory]
    [InlineData("Namespace0.Namespace1.ClassName")]
    [InlineData("Namespace0.ClassName")]
    [InlineData("ClassName")]
    [InlineData("Namespace0.Namespace1.ClassName(str1, str2)")]
    [InlineData("Namespace0.ClassName(str1, str2)")]
    [InlineData("ClassName(str1, str2)")]
    public void Decide_ShouldReturnSuppressedAndExistingSelector_WhenTestSelectorExists(string query)
    {
        // arrange
        var testSelector = Mock.Of<ITestSelector>();
        _testSelectors.Add(query, testSelector);

        // act
        var (shouldBeSuppressed, returnedTestSelector) = _decider.Decide(query, false, _testSelectors);

        // assert
        Assert.True(shouldBeSuppressed);
        Assert.Equal(testSelector, returnedTestSelector);
    }
}
