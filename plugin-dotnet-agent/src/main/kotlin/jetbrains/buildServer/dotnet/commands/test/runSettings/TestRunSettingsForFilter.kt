

package jetbrains.buildServer.dotnet.commands.test.runSettings

import jetbrains.buildServer.agent.Logger
import jetbrains.buildServer.asSequence
import jetbrains.buildServer.dotnet.DotnetCommandContext
import jetbrains.buildServer.dotnet.commands.test.TestRunSettingsProvider
import jetbrains.buildServer.dotnet.commands.test.TestsFilterProvider
import jetbrains.buildServer.dotnet.commands.test.splitting.TestsSplittingModeProvider
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class TestRunSettingsForFilter(
    private val _baseTestRunSettingsProvider: TestRunSettingsProvider,
    private val _testsFilterProvider: TestsFilterProvider,
    private val _testsSplittingModeProvider: TestsSplittingModeProvider
)
    : TestRunSettingsProvider {
    override fun tryCreate(context: DotnetCommandContext) =
            try {
                val testsSplittingMode = _testsSplittingModeProvider.getMode(context.toolVersion)
                _testsFilterProvider.getFilterExpression(testsSplittingMode)
                        .let { it.ifBlank { null } }
                        ?.let { newFilter ->
                            _baseTestRunSettingsProvider.tryCreate(context)?.let { settings ->
                                val testCaseFilter = ensureElementExists(settings, *_testCaseFilterPath)
                                testCaseFilter.textContent = arrayOf(testCaseFilter.textContent, newFilter)
                                        .filter { it.isNotBlank() }
                                        .map { "($it)" }
                                        .joinToString("&")

                                settings
                            }
                        }
            }
            catch (error: Throwable) {
                LOG.error(error)
                null
            }

    private fun ensureElementExists(doc: Document, vararg path: String): Element {
        var node: Node = doc
        for (name in path) {
            node = node.childNodes.asSequence<Element>().filter { name.equals(it.nodeName, true) }.lastOrNull()
                    ?: doc.createElement(name).let {
                        node.appendChild(it)
                        it
                    }
        }

        return node as Element
    }

    companion object {
        private val LOG = Logger.getLogger(TestRunSettingsForFilter::class.java)
        private val _testCaseFilterPath = arrayOf("RunSettings", "RunConfiguration", "TestCaseFilter")
    }
}