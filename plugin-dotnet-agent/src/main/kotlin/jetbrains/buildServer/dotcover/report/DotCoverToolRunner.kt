package jetbrains.buildServer.dotcover.report

import org.jdom2.Document
import java.io.IOException

interface DotCoverToolRunner {

    /**
     * Executes dotCover commandline.
     * Commandline is formed:
     * <pre>
     * dotCover.exe <command name> <configuration file name> [parameters]
    </configuration></command></pre> *
     *
     *
     * @param activityDisplayName activity name to be logged into build log
     * @param parameters - additional global commands that would be placed BEFORE xml-paraters xmlParametersCommand and file
     * @param command xml-parameters xmlParametersCommand of dotCover
     * @param config xml-parameters for a xmlParametersCommand
     * @throws IOException on execution or preparation error
     */
    @Throws(IOException::class)
    fun runDotCoverTool(activityDisplayName: String,
                        parameters: Collection<String>,
                        command: String,
                        config: Document)
}
