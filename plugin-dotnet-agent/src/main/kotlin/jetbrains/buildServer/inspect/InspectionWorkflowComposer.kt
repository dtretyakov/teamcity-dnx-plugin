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

package jetbrains.buildServer.inspect

import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agent.runner.*
import jetbrains.buildServer.rx.*
import jetbrains.buildServer.util.OSType

open class InspectionWorkflowComposer(
    private val _tool: InspectionTool,
    private val _toolStartCommandResolver: ToolStartCommandResolver,
    private val _argumentsProvider: ArgumentsProvider,
    private val _environmentProvider: EnvironmentProvider,
    private val _outputObserver: OutputObserver,
    private val _configurationFile: ConfigurationFile,
    private val _buildInfo: BuildInfo,
    private val _fileSystemService: FileSystemService,
    private val _pathsService: PathsService,
    private val _loggerService: LoggerService,
    private val _artifacts: ArtifactService,
    private val _virtualContext: VirtualContext,
    private val _inspectionToolStateWorkflowComposer: InspectionToolStateWorkflowComposer,
    private val _pluginParametersProvider: PluginParametersProvider
) : SimpleWorkflowComposer {

    override val target: TargetType = TargetType.Tool

    override fun compose(context: WorkflowContext, state: Unit, workflow: Workflow) =
        if (_buildInfo.runType == _tool.runnerType) Workflow(createCommandLines(context)) else Workflow()

    private fun createCommandLines(context: WorkflowContext) = sequence {
        val startCommand = _toolStartCommandResolver.resolve(_tool)

        var toolVersion: Version = Version.Empty
        if (_tool == InspectionTool.Inspectcode && _pluginParametersProvider.hasPluginParameters()) {
            val toolState = InspectionToolState(
                startCommand,
                observer { toolVersion = it }
            )
            yieldAll(_inspectionToolStateWorkflowComposer.compose(context, toolState).commandLines)

            if (toolVersion.isEmpty()) {
                _loggerService.writeWarning("Unable to resolve ${_tool.displayName} tool version, version-specific settings may not work correctly")
            }
        }

        val args = _argumentsProvider.getArguments(_tool, toolVersion)
        val virtualOutputPath = Path(_virtualContext.resolvePath((args.outputFile.absolutePath)))
        var hasErrors = false

        val commandLine = createCommandLine(startCommand, args, virtualOutputPath, toolVersion)
        disposableOf(
            context.filter { it.SourceId == commandLine.Id }.toOutput().subscribe(_outputObserver),
            context.filter { it.SourceId == commandLine.Id }.toErrors().subscribe { hasErrors = true },
            context.filter { it.SourceId == commandLine.Id }.toExitCodes().subscribe { exitCode -> onExit(exitCode, context, args, virtualOutputPath) }
        ).use {
            yield(commandLine)
        }

        if (hasErrors) {
            context.abort(BuildFinishedStatus.FINISHED_FAILED)
        }
    }

    protected open fun createCommandLine(
        startCommand: ToolStartCommand,
        args: InspectionArguments,
        virtualOutputPath: Path,
        toolVersion: Version
    ): CommandLine {
        val cmdArgs = sequence {
            yieldAll(startCommand.startArguments)
            yield(CommandLineArgument("--config=${_virtualContext.resolvePath(args.configFile.absolutePath)}"))
            if (args.debug) {
                yield(CommandLineArgument("--logFile=${_virtualContext.resolvePath(args.logFile.absolutePath)}"))
            }
            args.extensions?.let {
                yield(CommandLineArgument("--eXtensions=$it"))
            }

            yieldAll(args.customArguments)
        }

        _fileSystemService.write(args.configFile) {
            _configurationFile.create(
                it,
                virtualOutputPath,
                Path(_virtualContext.resolvePath(args.cachesHome.absolutePath)),
                args.debug
            )
        }

        return CommandLine(
            baseCommandLine = null,
            target = target,
            executableFile = startCommand.executable,
            workingDirectory = Path(_pathsService.getPath(PathType.Checkout).path),
            arguments = cmdArgs.toList(),
            environmentVariables = _environmentProvider.getEnvironmentVariables(toolVersion).toList()
        )
    }

    private fun onExit(exitCode: Int, context: WorkflowContext, args: InspectionArguments, virtualOutputPath: Path) {
        if (args.debug) {
            _artifacts.publish(_tool, args.logFile)
        }

        if (exitCode != 0) {
            if (
                exitCode < 0
                && _virtualContext.isVirtual
                && _virtualContext.targetOSType == OSType.WINDOWS
            ) {
                _loggerService.writeWarning("Windows Nano Server is not supported.")
            }

            _loggerService.buildFailureDescription("${_tool.displayName} execution failure.")
            context.abort(BuildFinishedStatus.FINISHED_FAILED)
        } else {
            if (_artifacts.publish(_tool, args.outputFile, Path(_tool.reportArtifactName))) {
                _loggerService.importData(_tool.dataProcessorType, virtualOutputPath)
            } else {
                _loggerService.buildFailureDescription("Output xml from ${_tool.displayName} is not found or empty on path ${args.outputFile.canonicalPath}.")
                context.abort(BuildFinishedStatus.FINISHED_FAILED)
            }
        }
    }
}