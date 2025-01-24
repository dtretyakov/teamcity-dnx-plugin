package jetbrains.buildServer.dotnet.test.agent.runner

import io.mockk.*
import io.mockk.impl.annotations.MockK
import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agent.runner.*
import jetbrains.buildServer.rx.*
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class WorkflowSessionTest {
    private val _events = mutableListOf<Notification<CommandResultEvent>>()
    private var _subscriptionToken = emptyDisposable()
    @MockK private lateinit var _workflowComposer: SimpleWorkflowComposer
    @MockK private lateinit var _commandExecutionFactory: CommandExecutionFactory
    @MockK private lateinit var _workflowSessionEventManager: WorkflowSessionEventManager
    private val _commandLine1 = CommandLine(null, TargetType.Tool, Path("dotnet 1"), Path("wd1"))
    private val _commandLine2 = CommandLine(null, TargetType.CodeCoverageProfiler, Path("dotnet 2"), Path("wd2"))
    @MockK private lateinit var _commandExecution1: CommandExecution
    @MockK private lateinit var _commandExecution2: CommandExecution

    @BeforeMethod
    fun setUp() {
        MockKAnnotations.init(this)
        clearAllMocks()
        _events.clear()
        every { _commandExecutionFactory.create(_commandLine1, any()) } returns _commandExecution1
        every { _commandExecutionFactory.create(_commandLine2, any()) } returns _commandExecution2
        every { _workflowSessionEventManager.notifySessionFinished(any()) } just Runs
    }

    @AfterMethod
    fun tearDown() {
        _subscriptionToken.dispose()
    }

    @Test
    fun shouldProvideCommandsToExecute() {
        // Given
        val session = createInstance()
        every { _workflowComposer.compose(session, Unit) } returns Workflow(_commandLine1, _commandLine2)

        // When
        val commands = session.toSequence().toList()

        // Then
        Assert.assertEquals(commands, listOf(_commandExecution1, _commandExecution2))
        Assert.assertEquals(session.status, WorkflowStatus.Completed)
        Assert.assertEquals(_events, listOf(NotificationCompleted.completed<CommandResultEvent>()))
    }

    @Test
    fun shouldBeInRunningStatusWhileHasCommandsToExecute() {
        // Given
        val session = createInstance()

        // When

        // Then
        every { _workflowComposer.compose(session, Unit) } returns Workflow(
                sequence {
                    yield(_commandLine1)
                    Assert.assertEquals(session.status, WorkflowStatus.Running)
                    yield(_commandLine2)
                })

        session.toSequence().toList()
    }

    @Test
    fun shouldProvideAllCommandsToExecuteWhenAborting() {
        // Given
        val session = createInstance()

        // When
        every { _workflowComposer.compose(session, Unit) } returns Workflow(
                sequence {
                    yield(_commandLine1)
                    session.abort(BuildFinishedStatus.FINISHED_FAILED)
                    yield(_commandLine2)
                })
        val commands = session.toSequence().toList()

        // Then
        Assert.assertEquals(commands, listOf(_commandExecution1, _commandExecution2))
        Assert.assertEquals(session.status, WorkflowStatus.Failed)
        Assert.assertEquals(_events, listOf(NotificationCompleted.completed<CommandResultEvent>()))
    }

    @Test
    fun shouldProvideAllCommandsToExecuteWhenSessionWasFinished() {
        // Given
        val session = createInstance()

        // When
        every { _workflowComposer.compose(session, Unit) } returns Workflow(
                sequence {
                    yield(_commandLine1)
                    session.sessionFinished()
                    yield(_commandLine2)
                })
        val commands = session.toSequence().toList()

        // Then
        Assert.assertEquals(commands, listOf(_commandExecution1, _commandExecution2))
        Assert.assertEquals(session.status, WorkflowStatus.Completed)
        Assert.assertEquals(_events, listOf(NotificationCompleted.completed<CommandResultEvent>()))
    }

    private fun createInstance(): WorkflowSessionImpl {
        val session =  WorkflowSessionImpl(_workflowComposer, _commandExecutionFactory, _workflowSessionEventManager)
        _subscriptionToken = session.materialize().subscribe { _events.add(it) }
        return session
    }
}

fun MultiCommandBuildSession.toSequence(): Sequence<CommandExecution> =
    sequence<CommandExecution> {
        var nextCommand: CommandExecution?
        do {
            nextCommand = this@toSequence.nextCommand
            if (nextCommand != null) {
                yield(nextCommand)
            }
        } while (nextCommand != null)
    }