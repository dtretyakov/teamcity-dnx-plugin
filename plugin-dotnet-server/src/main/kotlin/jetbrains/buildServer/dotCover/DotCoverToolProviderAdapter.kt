

package jetbrains.buildServer.dotCover

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.FileSystemService
import jetbrains.buildServer.ToolService
import jetbrains.buildServer.XmlDocumentService
import jetbrains.buildServer.dotnet.CoverageConstants
import jetbrains.buildServer.dotnet.CoverageConstants.BUNDLED_TOOL_VERSION
import jetbrains.buildServer.dotnet.CoverageConstants.DOTCOVER_DEPRECATED_PACKAGE_ID
import jetbrains.buildServer.dotnet.CoverageConstants.DOTCOVER_PACKAGE_ID
import jetbrains.buildServer.dotnet.DotnetConstants
import jetbrains.buildServer.find
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.tools.*
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.jdom2.JDOMException
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.IOException

class DotCoverToolProviderAdapter(
        private val _toolService: ToolService,
        private val _toolType: ToolType,
        private val _toolComparator: DotCoverToolComparator,
        private val _toolFilter: DotCoverPackageFilter,
        private val _packageIdResolver: DotCoverPackageIdResolver,
        private val _pluginDescriptor: PluginDescriptor,
        private val _fileSystem: FileSystemService,
        private val _xmlDocumentService: XmlDocumentService
) : ServerToolProviderAdapter() {

    override fun getType() = _toolType

    override fun getAvailableToolVersions(): MutableCollection<out ToolVersion> = getPackages()

    override fun tryGetPackageVersion(toolPackage: File): GetPackageVersionResult {
        val version = _toolService.getPackageVersion(toolPackage, *DOT_COVER_PACKAGES)
            ?: return super.tryGetPackageVersion(toolPackage)
        val packageId = _packageIdResolver.resolvePackageId(toolPackage.name)
            ?: return super.tryGetPackageVersion(toolPackage)

        return GetPackageVersionResult.version(DotCoverToolVersion(_toolType, version.toString(), packageId))
    }

    override fun fetchToolPackage(toolVersion: ToolVersion, targetDirectory: File): File {
        LOG.debug("Fetch package for version \"${toolVersion.version}\" to directory \"$targetDirectory\"")

        val dotCoverToolVersion = getPackages()
            .firstOrNull { it.version == toolVersion.version }
            ?: throw ToolException("Failed to find package $toolVersion")

        val downloadUrl = dotCoverToolVersion.downloadUrl
        val destinationFileName = dotCoverToolVersion.destinationFileName

        return _toolService.fetchToolPackage(dotCoverToolVersion, targetDirectory, downloadUrl, destinationFileName)
    }

    override fun unpackToolPackage(toolPackage: File, targetDirectory: File) {
        val pathPrefix = if (toolPackage.name.lowercase().endsWith(DotnetConstants.PACKAGE_NUGET_EXTENSION)) "tools/" else ""
        _toolService.unpackToolPackage(toolPackage, pathPrefix, targetDirectory, *DOT_COVER_PACKAGES)

        val toolVersion = tryGetPackageVersion(toolPackage).toolVersion
        val descriptorPath = File(targetDirectory, "teamcity-plugin.xml")
        if (shouldUseOriginalToolDescriptor(toolVersion, descriptorPath)) {
            return
        }
        val newDescriptorPath = File(_pluginDescriptor.pluginRoot, "server/tool-descriptors/dotcover-teamcity-plugin.xml")
        _fileSystem.copy(newDescriptorPath, descriptorPath)
    }

    private fun shouldUseOriginalToolDescriptor(toolVersion: ToolVersion?, originalDescriptor: File) =
        TeamCityProperties.getBooleanOrTrue("teamcity.dotCover.useOriginalToolDescriptor") &&
        toolVersion != null &&
        toolVersion is DotCoverToolVersion &&
        _fileSystem.isExists(originalDescriptor) &&
        _toolComparator.compare(toolVersion, firstDotCoverVersionWithCorrectToolDescriptor()) >= 0

    override fun getDefaultBundledVersionId(): String? = null

    private val bundledDotCoverTool by lazy(::fetchBundledDotCoverToolFromPlugin)

    private fun fetchBundledDotCoverToolFromPlugin(): Collection<InstalledToolVersion> {
        // All this logic is still needed for TeamCity Cloud
        val pluginRoot: File = _pluginDescriptor.pluginRoot
        val dotcoverBundledNuspecFilePath = TeamCityProperties.getProperty(
            "teamcity.dotCover.bundled.nuspec", CoverageConstants.DOTCOVER_BUNDLED_NUSPEC_FILE_PATH)
        val dotcoverBundledAgentToolPackagePath = TeamCityProperties.getProperty(
            "teamcity.dotCover.bundled.tool.package", CoverageConstants.DOTCOVER_BUNDLED_AGENT_TOOL_PACKAGE_PATH)
        val bundledNuspecFile = File(pluginRoot, dotcoverBundledNuspecFilePath)
        val bundledToolPackage = File(pluginRoot, dotcoverBundledAgentToolPackagePath)

        if (!bundledNuspecFile.isFile) {
            LOG.debug("Bundled dotCover nuspec file doesn't exist on path " + bundledNuspecFile.absolutePath)
            return super.getBundledToolVersions()
        }
        if (!bundledToolPackage.isFile) {
            LOG.debug("Bundled dotCover tool package doesn't exist on path " + bundledToolPackage.absolutePath)
            return super.getBundledToolVersions()
        }

        val result = mutableSetOf<InstalledToolVersion>()
        try {
            _fileSystem.read(bundledNuspecFile) {
                val doc = _xmlDocumentService.deserialize(it)
                val bundledPackageVersion = getContents(doc, "/package/metadata/version").firstOrNull()
                val bundledPackageId = getContents(doc, "/package/metadata/id").firstOrNull()

                if (bundledPackageVersion != null && bundledPackageId != null) {
                    result.add(SimpleInstalledToolVersion(
                        DotCoverToolVersion(_toolType, bundledPackageVersion, bundledPackageId),
                        null,
                        null,
                        bundledToolPackage))
                }
            }
        } catch (error: Exception) {
            when (error) {
                is JDOMException,
                is IOException -> {
                    LOG.warn("Failed to get version of bundled CLT from file " + bundledNuspecFile.absolutePath, error)
                    return super.getBundledToolVersions()
                }
                else -> throw error
            }
        }

        return result
    }

    override fun getBundledToolVersions(): Collection<InstalledToolVersion> = bundledDotCoverTool

    override fun getDownloadableBundledToolVersions(): MutableCollection<out ToolVersion> {
        return if (bundledDotCoverTool.isEmpty())
            mutableListOf(DotCoverToolVersion(_toolType, BUNDLED_TOOL_VERSION, DOTCOVER_PACKAGE_ID))
        else
            mutableListOf()
    }

    private fun getPackages() = _toolService.getPackages(*DOT_COVER_PACKAGES)
        .filter { _toolFilter.accept(it) }
        .map { DotCoverDownloadableToolVersion(_toolType, it) }
        .sortedWith(_toolComparator.reversed())
        .toMutableList()

    private fun firstDotCoverVersionWithCorrectToolDescriptor() = DotCoverToolVersion(
        _toolType, "2023.3", DOTCOVER_PACKAGE_ID
    )

    companion object {
        private val LOG: Logger = Logger.getInstance(DotCoverToolProviderAdapter::class.java.name)
        private val DOT_COVER_PACKAGES = arrayOf(DOTCOVER_DEPRECATED_PACKAGE_ID, DOTCOVER_PACKAGE_ID)

        fun getContents(doc: Document, xpath: String): Sequence<String> =
            doc.find<Element>(xpath).map { it.textContent }.filter { !it.isNullOrBlank() }
    }
}