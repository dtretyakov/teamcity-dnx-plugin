package jetbrains.buildServer.dotcover.report

import jetbrains.buildServer.dotnet.coverage.serviceMessage.DotnetCoverageParameters
import org.jdom2.Content
import org.jdom2.Document
import org.jdom2.Element
import java.io.File

open class DotCover26CommandsConfigFactory(
    protected val coverageParameters: DotnetCoverageParameters
) : DotCoverCommandsConfigFactory {

    override fun createMergeCommandConfig(sources: Collection<File>, output: File): Document {
        return object : Document() {
            init {
                addContent(object : Element("MergeParams") {
                    init {
                        addContent(object : Element("Source") {
                            init {
                                for (source in sources) {
                                    addContent(object : Element("string") {
                                        init { text = source.path }
                                    } as Content)
                                }
                            }
                        } as Content)
                        addContent(object : Element("TempDir") {
                            init { text = coverageParameters.getTempDirectory().path }
                        } as Content)
                        addContent(object : Element("Output") {
                            init { text = output.path }
                        } as Content)
                    }
                })
            }
        }
    }

    override fun createReportCommandConfig(source: File, output: File): Document {
        return object : Document() {
            init {
                addContent(object : Element("ReportParams") {
                    init {
                        addContent(object : Element("Source") {
                            init { text = source.path }
                        } as Content)
                        addContent(object : Element("Output") {
                            init { text = output.path }
                        } as Content)
                        addContent(object : Element("ReportType") {
                            init { text = "TeamCityXML" }
                        } as Content)
                    }
                })
            }
        }
    }

    override fun createZipCommandConfig(source: File, output: File): Document {
        return Document(
            object : Element("ZipParams") {
                init {
                    addContent(object : Element("Source") {
                        init { text = source.path }
                    } as Content)
                    addContent(object : Element("Output") {
                        init { text = output.path }
                    } as Content)
                }
            }
        )
    }

    override fun createDeleteCommandConfig(sources: Collection<File>): Document {
        return Document(
            object : Element("DeleteParams") {
                init {
                    addContent(object : Element("Source") {
                        init {
                            for (file in sources) {
                                addContent(object : Element("string") {
                                    init { text = file.path }
                                } as Content)
                            }
                        }
                    } as Content)
                }
            }
        )
    }
}
