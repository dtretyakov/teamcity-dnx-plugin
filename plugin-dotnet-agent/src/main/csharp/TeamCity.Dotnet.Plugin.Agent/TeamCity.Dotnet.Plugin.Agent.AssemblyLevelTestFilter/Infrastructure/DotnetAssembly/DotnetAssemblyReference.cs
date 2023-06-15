using Mono.Cecil;

namespace TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.DotnetAssembly;

internal class DotnetAssemblyReference : IDotnetAssemblyReference
{
    private readonly AssemblyNameReference _assemblyNameReference;

    public DotnetAssemblyReference(AssemblyNameReference assemblyNameReference)
    {
        _assemblyNameReference = assemblyNameReference;
    }
    
    public string FullName => _assemblyNameReference.FullName;

    public string Name => _assemblyNameReference.Name;
}