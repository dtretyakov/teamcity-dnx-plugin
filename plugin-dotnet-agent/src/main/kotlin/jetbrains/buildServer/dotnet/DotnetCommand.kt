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

package jetbrains.buildServer.dotnet

import jetbrains.buildServer.agent.CommandResultEvent
import jetbrains.buildServer.dotnet.commands.targeting.TargetArguments
import jetbrains.buildServer.rx.Observer

interface DotnetCommand : ArgumentsProvider {
    val toolResolver: ToolResolver

    val commandType: DotnetCommandType

    val command: Sequence<String>

    val isAuxiliary: Boolean

    val title: String

    val targetArguments: Sequence<TargetArguments>

    val environmentBuilders: Sequence<EnvironmentBuilder>

    val resultsAnalyzer: ResultsAnalyzer

    val resultsObserver: Observer<CommandResultEvent>
}

