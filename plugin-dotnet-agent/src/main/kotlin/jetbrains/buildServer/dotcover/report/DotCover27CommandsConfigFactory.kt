package jetbrains.buildServer.dotcover.report

import jetbrains.buildServer.dotnet.coverage.serviceMessage.DotnetCoverageParameters
import org.jdom2.Content
import org.jdom2.Document
import org.jdom2.Element
import java.io.File

open class DotCover27CommandsConfigFactory(
    coverageParameters: DotnetCoverageParameters
) : DotCover26CommandsConfigFactory(coverageParameters) {

    override fun createMergeCommandConfig(sources: Collection<File>, output: File): Document {
        return object : Document() {
            init {
                addContent(object : Element("MergeParams") {
                    init {
                        for (source in sources) {
                            addContent(object : Element("Source") {
                                init { text = source.path }
                            } as Content)
                        }
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
}
