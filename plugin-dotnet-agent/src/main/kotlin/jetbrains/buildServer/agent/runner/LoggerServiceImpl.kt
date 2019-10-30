package jetbrains.buildServer.agent.runner

import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.messages.DefaultMessagesInfo
import jetbrains.buildServer.messages.serviceMessages.BlockClosed
import jetbrains.buildServer.messages.serviceMessages.BlockOpened
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.rx.Disposable
import jetbrains.buildServer.rx.disposableOf

class LoggerServiceImpl(
        private val _buildStepContext: BuildStepContext,
        private val _colorTheme: ColorTheme)
    : LoggerService {

    private val listener: LoggingProcessListener
        get() = LoggingProcessListener(_buildLogger)

    private val _buildLogger: BuildProgressLogger
        get() = _buildStepContext.runnerContext.build.buildLogger

    override fun writeMessage(serviceMessage: ServiceMessage) = _buildLogger.message(serviceMessage.toString())

    override fun writeBuildProblem(buildProblem: BuildProblemData) = _buildLogger.logBuildProblem(buildProblem)

    override fun writeStandardOutput(text: String, color: Color) =
            listener.onStandardOutput(applyColor(text, color))

    override fun writeStandardOutput(vararg text: StdOutText) =
            listener.onStandardOutput(applyColor(*text))

    override fun writeErrorOutput(text: String) = listener.onErrorOutput(text)

    override fun writeBlock(blockName: String, description: String): Disposable {
        _buildLogger.message(BlockOpened(blockName, if (description.isBlank()) null else description).toString())
        return disposableOf { _buildLogger.message(BlockClosed(blockName).toString()) }
    }

    override fun writeTrace(text: String) =
        _buildLogger.logMessage(DefaultMessagesInfo.internalize(DefaultMessagesInfo.createTextMessage(text)))

    private fun applyColor(text: String, color: Color, prevColor: Color = Color.Default): String =
        if (color == prevColor) {
            text
        }
        else {
            if (color == Color.Default) {
                "\u001B[0m$text"
            } else {
                "\u001B[${_colorTheme.getAnsiColor(color)}m$text"
            }
        }

    private fun applyColor(vararg text: StdOutText): String =
            text.fold(DefaultStdOutText) {
                acc, (text, color) -> StdOutText(acc.text + applyColor(text, color, acc.color), color)
            }.text

    companion object {
        private val DefaultStdOutText = StdOutText("")
    }
}