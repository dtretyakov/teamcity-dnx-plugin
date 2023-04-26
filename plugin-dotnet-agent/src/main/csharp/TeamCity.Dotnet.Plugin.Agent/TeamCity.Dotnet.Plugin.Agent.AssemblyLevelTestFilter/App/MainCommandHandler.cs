/*
 * Copyright 2000-2023 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

using Microsoft.Extensions.Logging;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.CommandLine;
using TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.CommandLine.Help;

namespace TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.App;

internal class MainCommandHandler : ICommandHandler<MainCommand>
{
    private readonly IHelpPrinter _helpPrinter;
    private readonly ILogger<MainCommandHandler> _logger;

    public MainCommandHandler(IHelpPrinter helpPrinter, ILogger<MainCommandHandler> logger)
    {
        _helpPrinter = helpPrinter;
        _logger = logger;
    }

    public Task ExecuteAsync(MainCommand command)
    {
        if (command.Help)
        {
            _helpPrinter.ShowHelp(command);
            return Task.CompletedTask;
        }

        _logger.LogInformation($"No subcommand found in {nameof(MainCommand)}");
        return Task.CompletedTask;
    }
}
