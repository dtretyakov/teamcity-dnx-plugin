package jetbrains.buildServer.dotcover.report

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.dotnet.coverage.DotnetCoverageReportGeneratorRunner
import jetbrains.buildServer.dotnet.coverage.serviceMessage.DotnetCoverageParameters
import jetbrains.buildServer.dotnet.coverage.utils.TempFactory
import jetbrains.buildServer.util.FileUtil
import jetbrains.buildServer.util.XmlUtil
import org.jdom2.Document
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DotCoverToolRunnerImpl(
    private val _runner: DotnetCoverageReportGeneratorRunner,
    private val _coverageParameters: DotnetCoverageParameters,
    private val _tempFileFactory: TempFactory
) : DotCoverToolRunner {

    @Throws(IOException::class)
    private fun saveDocument(document: Document): File {
        val result: File = _tempFileFactory.createTempFile(_coverageParameters.getTempDirectory(),
            DOT_COVER_CONFIG_FILE_NAME, DOT_COVER_CONFIG_FILE_EXTENSION, 100)
        val fos = FileOutputStream(result)
        try {
            XmlUtil.saveDocument(document, fos)
        } finally {
            FileUtil.close(fos)
        }
        return result
    }

    @Throws(IOException::class)
    override fun runDotCoverTool(activityDisplayName: String,
                                 parameters: Collection<String>,
                                 command: String,
                                 config: Document) {

        val file = saveDocument(config)
        val argz: MutableList<String> = ArrayList()
        argz.add(command)
        argz.add(file.path)
        argz.addAll(parameters)

        LOG.info("Start dotCover with command: " + argz + " and parameters: " + XmlUtil.to_s(config.rootElement))
        try {
            _runner.runReportGenerator(activityDisplayName, argz)
        } finally {
            FileUtil.delete(file)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(DotCoverToolRunnerImpl::class.java.name)
        private const val DOT_COVER_CONFIG_FILE_NAME = "dotCover"
        private const val DOT_COVER_CONFIG_FILE_EXTENSION = ".xml"
    }
}
