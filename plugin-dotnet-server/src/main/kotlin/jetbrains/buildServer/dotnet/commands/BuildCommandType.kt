package jetbrains.buildServer.dotnet.commands

import jetbrains.buildServer.dotnet.DotnetCommandType
import jetbrains.buildServer.dotnet.requirements.SDKBasedRequirementFactory

/**
 * Provides parameters for dotnet build command.
 */
class BuildCommandType(
    sdkBasedRequirementFactory: SDKBasedRequirementFactory
) : DotnetCLICommandType(sdkBasedRequirementFactory) {
    override val name: String = DotnetCommandType.Build.id

    override val editPage: String = "editBuildParameters.jsp"

    override val viewPage: String = "viewBuildParameters.jsp"
}