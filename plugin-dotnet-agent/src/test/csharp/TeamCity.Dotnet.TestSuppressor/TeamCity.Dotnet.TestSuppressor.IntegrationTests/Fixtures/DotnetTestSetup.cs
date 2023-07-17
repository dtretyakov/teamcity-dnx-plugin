using System.Collections.Concurrent;
using DotNet.Testcontainers.Builders;
using DotNet.Testcontainers.Configurations;
using DotNet.Testcontainers.Containers;
using DotNet.Testcontainers.Images;

namespace TeamCity.Dotnet.TestSuppressor.IntegrationTests.Fixtures;

internal class DotnetTestSetup : IDisposable
{
    private static object _lock = new();
    private static readonly ConcurrentDictionary<DotnetVersion, IImage> DockerImages;
    private const string WorkDirPath = "/app";
    private const string TestsQueriesFileName = "tests-list.txt";
    private readonly string _hostAppSourcesPath = $"{CurrentDirectory}/published-app-binaries";
    private const string AppName = "TeamCity.Dotnet.TestSuppressor.dll";

    static DotnetTestSetup()
    {
        DockerImages = new ConcurrentDictionary<DotnetVersion, IImage>();
    }

    private static string CurrentDirectory => Directory.GetCurrentDirectory();

    public DotnetTestSetup(DotnetVersion dotnetVersion)
    {
        if (!DockerImages.ContainsKey(dotnetVersion))
        {
            lock (_lock)
            {
                if (!DockerImages.ContainsKey(dotnetVersion))
                {
                    DockerImages.TryAdd(dotnetVersion, new DotnetSdkImage(dotnetVersion));
                }
            }
        }

        var id = Guid.NewGuid();
        HostTestProjectPath = Directory.CreateDirectory($"{CurrentDirectory}/test-project__dotnet_{dotnetVersion}__{id}").FullName;
        Container = RunContainer(dotnetVersion, id).Result;
    }

    public static string MountedTestProjectSourcesDirPath => "/mounted__test-project-sources";

    public static string MountedFilterAppSourcesDirPath => "/mounted__app-binaries";

    public static string TestProjectSourcesDirPath => WorkDirPath + "/test-project";

    public static string AppDirPath => WorkDirPath + "/filter-app";

    public static string AppPath => AppDirPath + "/" + AppName;

    public string HostTestProjectPath { get; }

    public string HostTestsQueriesFilePath => $"{HostTestProjectPath}/{TestsQueriesFileName}";

    public static string TestsQueriesFilePath => $"{TestProjectSourcesDirPath}/{TestsQueriesFileName}";

    public IContainer Container { get; }

    public void ClearTestProjectDir() => ClearDirectory(HostTestProjectPath);

    private async Task<IContainer> RunContainer(DotnetVersion dotnetVersion, Guid id)
    {
        var container = new ContainerBuilder()
            .WithImage(DockerImages[dotnetVersion])
            .WithName($"tc-dotnet-test-suppressor__integration-tests__dotnet_{dotnetVersion}__{id}")
            .WithWorkingDirectory(WorkDirPath)
            .WithCommand("tail", "-f", "/dev/null")
            .WithBindMount(HostTestProjectPath, MountedTestProjectSourcesDirPath, AccessMode.ReadOnly)
            .WithBindMount(_hostAppSourcesPath, MountedFilterAppSourcesDirPath, AccessMode.ReadOnly)
            .WithCleanUp(true)
            .WithAutoRemove(true);

        var builtContainer = container.Build();
        await builtContainer.StartAsync();
        return builtContainer;
    }

    private static void ClearDirectory(string directoryPath)
    {
        foreach (var file in Directory.GetFiles(directoryPath))
        {
            File.Delete(file);
        }

        foreach (var subdirectory in Directory.GetDirectories(directoryPath))
        {
            ClearDirectory(subdirectory);
        }
    }

    public void Dispose()
    {
        Container.DisposeAsync().AsTask().Wait();

        ClearDirectory(HostTestProjectPath);
        Directory.Delete(HostTestProjectPath);
    }
}