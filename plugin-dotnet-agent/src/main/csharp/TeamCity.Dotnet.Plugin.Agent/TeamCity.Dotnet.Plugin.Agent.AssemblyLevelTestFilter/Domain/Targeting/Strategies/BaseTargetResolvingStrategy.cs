// /*
//  * Copyright 2000-2023 JetBrains s.r.o.
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  * http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

using Microsoft.Extensions.Logging;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.FS;

namespace TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Domain.Targeting.Strategies;

internal abstract class BaseTargetResolvingStrategy : ITargetResolvingStrategy
{
    private readonly IFileSystem _fileSystem;
    private readonly ILogger<BaseTargetResolvingStrategy> _logger;

    protected BaseTargetResolvingStrategy(
        IFileSystem fileSystem,
        ILogger<BaseTargetResolvingStrategy> logger)
    {
        _fileSystem = fileSystem;
        _logger = logger;
    }

    public abstract TargetType TargetType { get; }
    
    protected abstract IEnumerable<string> AllowedTargetExtensions { get; }

    public abstract IEnumerable<(FileInfo, TargetType)> Resolve(string target);

    protected FileInfo? TryToGetTargetFile(string target)
    {
        if (!_fileSystem.FileExists(target))
        {
            _logger.LogWarning("Target {TargetType} not found: {Target}", TargetType, target);
            return null;
        }

        var (assemblyFile, exception) = _fileSystem.GetFileInfo(target);
        if (exception != null)
        {
            _logger.Log(LogLevel.Warning, exception,"Can't access to target {TargetType} file: {Target}", TargetType, target);
            return null;
        }
        if (AllowedTargetExtensions.All(e => e != assemblyFile!.Extension))
        {
            _logger.LogWarning(
                "Target {TargetType} has unsupported extension: {Target}. Supported extensions are: {AllowedExtensions}", 
                TargetType,
                target,
                AllowedTargetExtensions
            );
            return null;
        }

        return assemblyFile;
    }
}