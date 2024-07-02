package jetbrains.buildServer.dotcover.report

import jetbrains.buildServer.dotnet.CoverageConstants.COVERAGE_REPORT_EXT
import jetbrains.buildServer.dotnet.CoverageConstants.COVERAGE_REPORT_NAME
import jetbrains.buildServer.dotnet.coverage.serviceMessage.DotnetCoverageParameters
import jetbrains.buildServer.dotnet.coverage.utils.TempFactory
import org.jdom2.Content
import org.jdom2.Document
import org.jdom2.Element
import java.io.File
import java.io.IOException

class DotCover1_0_ReporterImpl(
    private val _runner: DotCoverToolRunner,
    private val _coverageParameters: DotnetCoverageParameters,
    private val _tempFactory: TempFactory
) : DotCoverReporterTool {

    @Throws(IOException::class)
    override fun runDeleteTask(files: Collection<File>) {
        val doc = Document(
            object : Element("DeleteParams") {
                init {
                    addContent(object : Element("Source") {
                        init {
                            for (file in files) {
                                addContent(object : Element("string") {
                                    init {
                                        text = file.path
                                    }
                                } as Content)
                            }
                        }
                    } as Content)
                }
            }
        )
        _runner.runDotCoverTool("Remove dotCover snapshot files", emptyList(), "delete", doc)
    }

    @Throws(IOException::class)
    override fun runReportTask(reportFile: File): File {
        val resultFile: File = _tempFactory.createTempFile(
            _coverageParameters.getTempDirectory(),
            COVERAGE_REPORT_NAME,
            COVERAGE_REPORT_EXT,
            100)

        val doc: Document = object : Document() {
            init {
                addContent(object : Element("ReportParams") {
                    init {
                        addContent(object : Element("Source") {
                            init { text = reportFile.path }
                        } as Content)
                        addContent(object : Element("Output") {
                            init { text = resultFile.path }
                        } as Content)
                        addContent(object : Element("IncludeStatementInfo") {
                            init { text = "true" }
                        } as Content)
                    }
                })
            }
        }

        _runner.runDotCoverTool("Generate dotCover report", emptyList(), "report", doc)
        return resultFile
    }

    @Throws(IOException::class)
    override fun runMergeTask(reportFiles: Collection<File>): File {
        val reportFile: File = _tempFactory.createTempFile(
            _coverageParameters.getTempDirectory(),
            "dotCoverSnapshot",
            ".dcvr",
            100)

        val doc: Document = object : Document() {
            init {
                addContent(object : Element("MergeParams") {
                    init {
                        addContent(object : Element("Source") {
                            init {
                                for (file in reportFiles) {
                                    addContent(object : Element("string") {
                                        init { text = file.path }
                                    } as Content)
                                }
                            }
                        } as Content)
                        addContent(object : Element("TempDir") {
                            init { text = _coverageParameters.getTempDirectory().path }
                        } as Content)
                        addContent(object : Element("Output") {
                            init { text = reportFile.path }
                        } as Content)
                    }
                })
            }
        }

        _runner.runDotCoverTool("Merge dotCover reports", emptyList(), "merge", doc)
        return reportFile
    }
}
