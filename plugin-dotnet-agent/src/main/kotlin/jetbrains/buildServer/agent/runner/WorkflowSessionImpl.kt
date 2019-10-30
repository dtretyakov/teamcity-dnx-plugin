/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * See LICENSE in the project root for license information.
 */

package jetbrains.buildServer.agent.runner

import jetbrains.buildServer.agent.*
import jetbrains.buildServer.rx.Disposable
import jetbrains.buildServer.rx.Observer
import jetbrains.buildServer.rx.emptyDisposable
import jetbrains.buildServer.rx.subjectOf
import org.jetbrains.kotlin.utils.join
import java.io.File

class WorkflowSessionImpl(
        private val _workflowComposer: WorkflowComposer,
        private val _buildStepContext: BuildStepContext,
        private val _loggerService: LoggerService,
        private val _commandLinePresentationService: CommandLinePresentationService,
        private val _argumentsService: ArgumentsService,
        private val _virtualContext: VirtualContext)
    : MultiCommandBuildSession, WorkflowContext {

    private val _commandLinesIterator = lazy { _workflowComposer.compose(this).commandLines.iterator() }
    private val _eventSource = subjectOf<CommandResultEvent>()
    private var _buildFinishedStatus: BuildFinishedStatus? = null

    override fun subscribe(observer: Observer<CommandResultEvent>) = _eventSource.subscribe(observer)

    override fun getNextCommand(): CommandExecution? {
        if (status != WorkflowStatus.Running) {
            @Suppress("ControlFlowWithEmptyBody")
            // It is required to run code after yields
            while (_commandLinesIterator.value.hasNext()) {}
            return null
        }

        // yield command here
        if (!_commandLinesIterator.value.hasNext()) {
            if (_buildFinishedStatus == null) {
                _buildFinishedStatus = BuildFinishedStatus.FINISHED_SUCCESS
            }

            return null
        }

        var nextCommand = _commandLinesIterator.value.next()

        return CommandExecutionAdapter(
                nextCommand,
                _buildStepContext,
                _loggerService,
                _eventSource,
                _commandLinePresentationService,
                _argumentsService,
                _virtualContext)
    }

    override val status: WorkflowStatus
        get() =
            when (_buildFinishedStatus) {
                null -> WorkflowStatus.Running
                BuildFinishedStatus.FINISHED_SUCCESS, BuildFinishedStatus.FINISHED_WITH_PROBLEMS -> WorkflowStatus.Completed
                else -> WorkflowStatus.Failed
            }

    override fun abort(buildFinishedStatus: BuildFinishedStatus) {
        _buildFinishedStatus = buildFinishedStatus
    }

    override fun sessionStarted() = Unit

    override fun sessionFinished(): BuildFinishedStatus? {
        _eventSource.onComplete()
        return _buildFinishedStatus ?: BuildFinishedStatus.FINISHED_SUCCESS
    }

    private class CommandExecutionAdapter(
            private val _commandLine: CommandLine,
            private val _buildStepContext: BuildStepContext,
            private val _loggerService: LoggerService,
            private val _eventSource: Observer<CommandResultEvent>,
            private val _commandLinePresentationService: CommandLinePresentationService,
            private val _argumentsService: ArgumentsService,
            private val _virtualContext: VirtualContext) : CommandExecution, BuildProgressLoggerAware {

        private val _logger = SuppressingLogger(_buildStepContext.runnerContext.build.buildLogger, _isHiddenInBuidLog)
        private var _blockToken: Disposable = emptyDisposable()

        override fun beforeProcessStarted() = Unit

        override fun processStarted(programCommandLine: String, workingDirectory: File) {
            if (!_commandLine.title.isNullOrBlank())
            {
                if (_isHiddenInBuidLog) {
                    _loggerService.writeStandardOutput(_commandLine.title)
                }
                else {
                    _blockToken = _loggerService.writeBlock(_commandLine.title)
                }
            }

            if (_commandLine.description.any()) {
                writeStandardOutput(*_commandLine.description.toTypedArray())
            }

            val executableFilePresentation = _commandLinePresentationService.buildExecutablePresentation(_commandLine.executableFile)
            val argsPresentation = _commandLinePresentationService.buildArgsPresentation(_commandLine.arguments)

            writeStandardOutput(*(listOf(StdOutText("Starting: ")) + executableFilePresentation + argsPresentation).toTypedArray())
            val virtualWorkingDirectory = _virtualContext.resolvePath(_commandLine.workingDirectory.path)
            writeStandardOutput(StdOutText("in directory: "), StdOutText(virtualWorkingDirectory))
        }

        override fun processFinished(exitCode: Int) {
            _eventSource.onNext(CommandResultExitCode(exitCode))
            _blockToken.dispose()
        }

        override fun makeProgramCommandLine(): ProgramCommandLine = ProgramCommandLineAdapter(
                _argumentsService,
                _commandLine,
                _buildStepContext.runnerContext.buildParameters.environmentVariables)

        override fun onStandardOutput(text: String) {
            _eventSource.onNext(CommandResultOutput(text))
            writeStandardOutput(text)
        }

        override fun onErrorOutput(error: String) {
            _eventSource.onNext(CommandResultOutput(error))
            _loggerService.writeErrorOutput(error)
        }

        override fun interruptRequested(): TerminationAction = TerminationAction.KILL_PROCESS_TREE

        override fun isCommandLineLoggingEnabled(): Boolean = false

        override fun getLogger(): BuildProgressLogger = _logger

        private val _isHiddenInBuidLog get() = _commandLine.target == TargetType.SystemDiagnostics

        private fun writeStandardOutput(text: String) {
            if (!_isHiddenInBuidLog) {
                _loggerService.writeStandardOutput(text)
            }
            else {
                _loggerService.writeTrace(text)
            }
        }

        private fun writeStandardOutput(vararg text: StdOutText) {
            if (!_isHiddenInBuidLog) {
                _loggerService.writeStandardOutput(*text)
            }
            else {
                _loggerService.writeTrace(join(text.map { it.text }, " "))
            }
        }

        class SuppressingLogger(
                private val _baseLogger: BuildProgressLogger,
                private val _isHiddenInBuidLog: Boolean):
                BuildProgressLogger by _baseLogger {

            override fun warning(message: String?) {
                _baseLogger.debug(message)
            }

            override fun message(message: String?) {
                if (!_isHiddenInBuidLog) {
                    _baseLogger.message(message)
                } else {
                    _baseLogger.debug(message)
                }
            }
        }
    }

    private class ProgramCommandLineAdapter(
            private val _argumentsService: ArgumentsService,
            private val _commandLine: CommandLine,
            private val _environmentVariables: Map<String, String>)
        : ProgramCommandLine {

        override fun getExecutablePath(): String = _commandLine.executableFile.path

        override fun getWorkingDirectory(): String = _commandLine.workingDirectory.path

        override fun getArguments(): MutableList<String> = _commandLine.arguments.map { _argumentsService.normalize(it.value) }.toMutableList()

        override fun getEnvironment(): MutableMap<String, String> {
            val environmentVariables = _environmentVariables.toMutableMap()
            _commandLine.environmentVariables.forEach { environmentVariables[it.name] = it.value }
            return environmentVariables
        }
    }
}