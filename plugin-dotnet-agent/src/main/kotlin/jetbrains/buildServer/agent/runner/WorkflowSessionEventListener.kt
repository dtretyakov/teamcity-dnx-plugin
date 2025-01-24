package jetbrains.buildServer.agent.runner

import jetbrains.buildServer.agent.BuildFinishedStatus

interface WorkflowSessionEventListener {

    fun onSessionStarted()

    fun onSessionFinished(status: BuildFinishedStatus)
}