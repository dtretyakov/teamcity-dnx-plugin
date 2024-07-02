package jetbrains.buildServer.dotcover.report

import org.jdom2.Document
import java.io.File

interface DotCoverCommandsConfigFactory {

    fun createMergeCommandConfig(sources: Collection<File>, output: File): Document

    fun createReportCommandConfig(source: File, output: File): Document

    fun createZipCommandConfig(source: File, output: File): Document

    fun createDeleteCommandConfig(sources: Collection<File>): Document
}
