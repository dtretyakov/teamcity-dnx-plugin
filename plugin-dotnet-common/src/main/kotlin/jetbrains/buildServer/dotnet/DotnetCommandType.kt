package jetbrains.buildServer.dotnet

enum class DotnetCommandType(val id: String) {
    Build("build"),
    Pack("pack"),
    Publish("publish"),
    Restore("restore"),
    Test("test"),
    ListTests("list-tests"),
    Run("run"),
    NuGetPush("nuget-push"),
    NuGetDelete("nuget-delete"),
    NuGetLocals("nuget-locals"),
    Clean("clean"),
    MSBuild("msbuild"),
    VSTest("vstest"),
    VisualStudio("devenv"),
    Custom("custom"),
}