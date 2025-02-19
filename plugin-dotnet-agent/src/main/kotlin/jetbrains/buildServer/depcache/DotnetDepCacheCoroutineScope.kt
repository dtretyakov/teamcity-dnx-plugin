package jetbrains.buildServer.depcache

import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.executors.ExecutorsFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

class DotnetDepCacheCoroutineScope(
    private val _eventDispatcher: EventDispatcher<AgentLifeCycleListener>
) : CoroutineScope {

    private val scope: CoroutineScope

    init {
        scope = CoroutineScope(
            ExecutorsFactory
                .newFixedDaemonExecutor(EXECUTOR_NAME, THREAD_POOL_SIZE)
                .asCoroutineDispatcher(),
        )

        _eventDispatcher.addListener(object : AgentLifeCycleAdapter() {
            override fun agentShutdown() {
                scope.cancel("Agent is stopping")
            }
        })
    }

    override val coroutineContext: CoroutineContext
        get() = scope.coroutineContext

    private companion object {
        const val EXECUTOR_NAME = "Nuget Caches"
        const val THREAD_POOL_SIZE = 1
    }
}