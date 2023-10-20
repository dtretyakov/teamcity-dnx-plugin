using System.IO.Abstractions;
using Microsoft.Build.Framework;
using Microsoft.Build.Logging.StructuredLogger;
using Microsoft.Extensions.Logging;
using TeamCity.Dotnet.TestSuppressor.Infrastructure;
using TeamCity.Dotnet.TestSuppressor.Infrastructure.FileSystemExtensions;

namespace TeamCity.Dotnet.TestSuppressor.Domain.Targeting.Strategies;

/// <summary>
/// Try to parse assemblies from MSBuild .binlog file
/// About binary log: https://github.com/dotnet/msbuild/blob/main/documentation/wiki/Binary-Log.md
/// Binary log parser: https://github.com/KirillOsenkov/MSBuildStructuredLog
/// </summary>
internal class MsBuildBinlogTargetResolvingStrategy : BaseTargetResolvingStrategy
{
    private readonly ILogger<MsBuildBinlogTargetResolvingStrategy> _logger;
    
    public override TargetType TargetType => TargetType.MsBuildBinlog;
    
    public MsBuildBinlogTargetResolvingStrategy(
        IFileSystem fileSystem,
        ILogger<MsBuildBinlogTargetResolvingStrategy> logger) : base(fileSystem, logger)
    {
        _logger = logger;
    }

    protected override IEnumerable<string> AllowedTargetExtensions => new[] { FileExtension.MsBuildBinaryLog };

    public override IEnumerable<(IFileSystemInfo, TargetType)> Resolve(string target)
    {
        _logger.LogInformation("Resolving target MSBuild .binlog file: {Target}", target);
        
        var targetPathSystemInfo = TryToGetTargetFile(target);
        if (targetPathSystemInfo == null)
        {
            _logger.LogWarning("Invalid MSBuild .binlog target: {Target}", target);
            yield break;
        }
        
        var binlogFile = targetPathSystemInfo as IFileInfo;
        
        var outputAssemblyPathsResult = GetOutputAssemblyPaths(binlogFile!);
        if (outputAssemblyPathsResult.IsError)
        {
            _logger.LogWarning(
                outputAssemblyPathsResult.ErrorValue,
                "Target MSBuild .binlog {TargetProject} is invalid: {Reason}",
                binlogFile!.FullName,
                outputAssemblyPathsResult.ErrorValue.Message
            );
            yield break;
        }

        foreach (var outputAssemblyPath in outputAssemblyPathsResult.Value)
        {
            var assemblyFileInfoResult = FileSystem.TryGetFileInfo(outputAssemblyPath!);
            if (assemblyFileInfoResult.IsError)
            {
                _logger.LogWarning(assemblyFileInfoResult.ErrorValue, "Target MSBuild .binlog output file {TargetProjectOutputFile} does not exist", binlogFile!.FullName);
                yield break;
            }

            var assemblyFileInfo = assemblyFileInfoResult.Value;
        
            _logger.LogInformation("Resolved assembly by target MSBuild .binlog file: {Assembly}", assemblyFileInfo.FullName);
            yield return (assemblyFileInfo, TargetType.Assembly);
        }
    }

    private static Result<IEnumerable<string>, Exception> GetOutputAssemblyPaths(IFileSystemInfo binlogFile)
    {
        try
        {
            var result = new HashSet<string>();
            var reader = new BinLogReader();

            foreach (var record in reader.ReadRecords(binlogFile.FullName).Where(r => r.Args != null))
            {
                // heuristic #1: analyze Build target outputs to find output assemblies
                if (record.Args is TargetFinishedEventArgs { TargetName: "Build", TargetOutputs: not null } targetArgs)
                {
                    foreach (ITaskItem output in targetArgs.TargetOutputs)
                    {
                        var outputPath = output.ItemSpec.Trim();
                        if (outputPath.HasTargetFileExtension(TargetType.Assembly))
                        {
                            result.Add(outputPath);
                        }
                    }
                }
                
                // heuristic #2: looking for log entries of adding item with moniker-specific target path
                // useful for .sln/.csproj files with <TargetFrameworks> tag instead of <TargetFramework>;
                // we expect to find a message like
                // ```
                // AddItem: TargetPathWithTargetPlatformMoniker
                //      /absolute/path/to/the/target/assembly.dll
                //          ...something else...
                // ```
                else if (record.Args.Message != null && record.Args.Message.StartsWith("AddItem: TargetPathWithTargetPlatformMoniker"))
                {
                    var outputPath = record.Args.Message
                        .Split(Environment.NewLine).Take(1..2).DefaultIfEmpty(string.Empty).First().Trim();
                    if (outputPath.HasTargetFileExtension(TargetType.Assembly))
                    {
                        result.Add(outputPath);
                    }
                }
            }
            
            return Result<IEnumerable<string>, Exception>.Success(result);
        }
        catch (Exception exception)
        {
            return Result<IEnumerable<string>, Exception>.Error(exception);
        }
    }
}