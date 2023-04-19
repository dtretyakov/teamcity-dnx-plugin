namespace TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.TestEngines;

internal class TestAttribute
{
    public TestAttribute(TestAttributeType type, string name)
    {
        Type = type;
        Name = name;
    }

    public TestAttributeType Type { get; }
    
    public string Name { get; }
}