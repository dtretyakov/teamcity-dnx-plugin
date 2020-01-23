/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.dotnet

import jetbrains.buildServer.agent.CommandLineArgument
import jetbrains.buildServer.agent.runner.ParametersService

class RunCommand(
        _parametersService: ParametersService,
        override val resultsAnalyzer: ResultsAnalyzer,
        private val _targetService: TargetService,
        private val _customArgumentsProvider: ArgumentsProvider,
        override val toolResolver: DotnetToolResolver)
    : DotnetCommandBase(_parametersService) {

    override val commandType: DotnetCommandType
        get() = DotnetCommandType.Run

    override val targetArguments: Sequence<TargetArguments>
        get() = _targetService.targets.map {
            TargetArguments(sequenceOf(CommandLineArgument("--project"), CommandLineArgument(it.targetFile.path)))
        }

    override fun getArguments(context: DotnetBuildContext): Sequence<CommandLineArgument> = sequence {
        parameters(DotnetConstants.PARAM_FRAMEWORK)?.trim()?.let {
            if (it.isNotBlank()) {
                yield(CommandLineArgument("--framework"))
                yield(CommandLineArgument(it))
            }
        }

        parameters(DotnetConstants.PARAM_CONFIG)?.trim()?.let {
            if (it.isNotBlank()) {
                yield(CommandLineArgument("--configuration"))
                yield(CommandLineArgument(it))
            }
        }

        parameters(DotnetConstants.PARAM_RUNTIME)?.trim()?.let {
            if (it.isNotBlank()) {
                yield(CommandLineArgument("--runtime"))
                yield(CommandLineArgument(it))
            }
        }

        var customArgs = _customArgumentsProvider.getArguments(context).toList()
        if (customArgs.any()) {
            if (customArgs[0].value.trim() != "--") {
               yield(CommandLineArgument("--"))
            }

            yieldAll(_customArgumentsProvider.getArguments(context))
        }
    }
}