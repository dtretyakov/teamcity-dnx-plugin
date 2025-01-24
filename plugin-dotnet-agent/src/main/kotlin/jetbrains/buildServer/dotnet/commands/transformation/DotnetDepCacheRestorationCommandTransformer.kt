package jetbrains.buildServer.dotnet.commands.transformation

import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agent.runner.BuildStepContext
import jetbrains.buildServer.depcache.DotnetDepCacheBuildStepContext
import jetbrains.buildServer.depcache.DotnetDepCacheBuildStepContextHolder
import jetbrains.buildServer.depcache.DotnetDepCacheManager
import jetbrains.buildServer.dotnet.*
import jetbrains.buildServer.dotnet.commands.NugetLocalsCommand
import jetbrains.buildServer.dotnet.commands.targeting.TargetArguments
import jetbrains.buildServer.rx.Observer
import jetbrains.buildServer.rx.disposableOf

class DotnetDepCacheRestorationCommandTransformer(
    private val _nugetLocalsCommand: NugetLocalsCommand,
    private val _dotnetDepCacheManager: DotnetDepCacheManager,
    private val _restorePackagesPathArgumentsProvider: ArgumentsProvider,
    private val _restorePackagesPathManager: RestorePackagesPathManager,
    private val _buildStepContext: BuildStepContext,
    private val _virtualContext: VirtualContext,
    private val _buildStepContextHolder: DotnetDepCacheBuildStepContextHolder
) : DotnetCommandsTransformer {

    override val stage = DotnetCommandsTransformationStage.DepCacheRestoration

    override fun apply(context: DotnetCommandContext, commands: DotnetCommandsStream): DotnetCommandsStream {
        return sequence {
            for (initialCommand in commands) {
                val shouldTransform = _dotnetDepCacheManager.cacheEnabled &&
                        versionCompatible(context.toolVersion) &&
                        commandDepCacheCompatible(initialCommand) &&
                        targetArgumentsDepCacheCompatible(initialCommand)

                if (shouldTransform) {
                    registerAndRestoreCache()

                    // executing the initial command
                    yield(OverriddenRestorePackagesPathDotnetCommand(initialCommand, _restorePackagesPathArgumentsProvider, overrideRestorePackagesPath))
                } else {
                    yield(initialCommand)
                }
            }
        }
    }

    private suspend fun SequenceScope<DotnetCommand>.registerAndRestoreCache() {
        val context = _buildStepContextHolder.context
        if (context == null) {
            _dotnetDepCacheManager.cache?.logWarning(
                "Couldn't register and restore cache: Nuget cache step context wasn't initialized"
            )
            return
        }

        if (overrideRestorePackagesPath) {
            // since the RestorePackagesPath was overridden by TC, the cache root location is known, so the dependency cache can be registered and restored immediately
            val agentConfiguration = _buildStepContext.runnerContext.build.agentConfiguration
            val restorePackagesPath = _restorePackagesPathManager.getRestorePackagesPathLocation(agentConfiguration)
            _dotnetDepCacheManager.cache?.logMessage("running the build inside a Docker container with enabled package caching: setting the RestorePackagesPath MSBuild property to $restorePackagesPath. " +
                    "The property will be reset to its initial value once the build finishes")
            _dotnetDepCacheManager.registerAndRestoreCache(context, restorePackagesPath)
        } else {
            // execute a 'nuget locals' auxiliary command before the initial one to detect the cache root location, then register and restore the dependency cache
            val nugetPackagesGlobalDirObserver = CommandLineOutputAccumulationObserver()
            val registerCacheEnvBuilder = getRegisterCacheEnvBuilder(context, nugetPackagesGlobalDirObserver)
            yield(ObservingDotnetCommand(_nugetLocalsCommand, nugetPackagesGlobalDirObserver, listOf(registerCacheEnvBuilder)))
        }
    }

    private val overrideRestorePackagesPath
        get() = _restorePackagesPathManager.shouldOverrideRestorePackagesPath() &&
                _dotnetDepCacheManager.cacheEnabled &&
                _virtualContext.isVirtual

    private fun getRegisterCacheEnvBuilder(
        depCacheContext: DotnetDepCacheBuildStepContext,
        nugetPackagesGlobalDirObserver: CommandLineOutputAccumulationObserver
    ) = object : EnvironmentBuilder {
        override fun build(context: DotnetCommandContext): EnvironmentBuildResult {
            return EnvironmentBuildResult(
                // will be executed after the command
                disposable = disposableOf {
                    _dotnetDepCacheManager.registerAndRestoreCache(depCacheContext, nugetPackagesGlobalDirObserver)
                }
            )
        }
    }

    private fun versionCompatible(toolVersion: Version): Boolean {
        val compatible = toolVersion != Version.Empty && toolVersion >= MinDotNetSdkVersionForDepCache
        if (!compatible) {
            _dotnetDepCacheManager.cache?.logWarning(
                """the dependency cache is enabled but couldn't be used.
                Please update the .NET version.
                The minimum required .NET version for the dependency cache: ${MinDotNetSdkVersionForDepCache.toString()}.
                The version used for the build: ${toolVersion.toString()}.
                 """.trimIndent()
            )
        }
        return compatible
    }

    private fun String.endsWithIgnoreCase(suffix: String): Boolean {
        return this.lowercase().endsWith(suffix.lowercase())
    }

    private fun targetArgumentsDepCacheCompatible(command: DotnetCommand): Boolean {
        return command.targetArguments.flatMap { it.arguments }.none { argument ->
            UnsupportedTargets.any { unsupported ->
                argument.value.endsWithIgnoreCase(unsupported)
            }
        }
    }

    private fun commandDepCacheCompatible(command: DotnetCommand) = !UnsupportedCommands.contains(command.commandType)

    private class ObservingDotnetCommand(
        private val _originalCommand: DotnetCommand,
        private val _resultObserver: Observer<CommandResultEvent>,
        override val environmentBuilders: List<EnvironmentBuilder>,
        private val _targetArguments: Sequence<TargetArguments> = emptySequence()
    ) : DotnetCommand by _originalCommand {
        override val resultsObserver = _resultObserver
        override val targetArguments: Sequence<TargetArguments> get() = _targetArguments
    }

    private class OverriddenRestorePackagesPathDotnetCommand(
        private val _originalCommand: DotnetCommand,
        private val _restorePackagesPathArgumentsProvider: ArgumentsProvider,
        private val _overrideRestorePackagesPath: Boolean
    ) : DotnetCommand by _originalCommand {
        override fun getArguments(context: DotnetCommandContext): Sequence<CommandLineArgument> {
            if (_overrideRestorePackagesPath) {
                return _originalCommand.getArguments(context) + _restorePackagesPathArgumentsProvider.getArguments(context)
            }
            return _originalCommand.getArguments(context)
        }
    }

    companion object {
        val MinDotNetSdkVersionForDepCache = Version(6, 0, 0)

        private val UnsupportedCommands = setOf(
            DotnetCommandType.VisualStudio,
            DotnetCommandType.Custom,
            DotnetCommandType.VSTest,
            DotnetCommandType.NuGetPush,
            DotnetCommandType.NuGetDelete
        )

        private val UnsupportedTargets = setOf(".dll", ".exe")
    }
}