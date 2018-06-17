package org.elixir_lang.run

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.ExternalizablePath
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import org.elixir_lang.run.configuration.Module
import org.jdom.Element
import java.io.File

fun ensureWorkingDirectory(project: Project) = project.basePath!!

fun ensureWorkingDirectory(project: Project, module: com.intellij.openapi.module.Module?): String {
    var workingDirectory: String? = null

    if (module != null) {
        workingDirectory = workingDirectory(module)
    }

    if (workingDirectory.isNullOrBlank()) {
        workingDirectory = ensureWorkingDirectory(project)
    }

    return workingDirectory!!
}

fun List<String>.toArguments(): String? {
    val joined = ParametersList.join(this)

    return if (joined.isBlank()) {
        null
    } else {
        joined
    }
}

fun MutableList<String>.fromArguments(arguments: String?) {
    this.clear()

    arguments
            ?.let { ParametersList.parse(it) }
            ?.let { this.addAll(it) }
}

fun Element.writeExternalArgumentList(command: String, argumentList: List<String>) {
    if (argumentList.isNotEmpty()) {
        val commandElement = Element(command)

        argumentList.forEach {
            val argumentElement = Element(ARGUMENT)
            argumentElement.addContent(it)
            commandElement.addContent(argumentElement)
        }

        addContent(commandElement)
    }
}

fun Element.readExternalArgumentList(command: String, argumentList: MutableList<String>) {
    argumentList.clear()

    val newArgumentList = getChild(command)?.let { commandElement ->
        commandElement.getChildren(ARGUMENT).map { argumentElement ->
            argumentElement.text
        }
    } ?: emptyList()

    argumentList.addAll(newArgumentList)
}

fun Element.writeExternalModule(configuration: Configuration) {
    val moduleName = configuration.configurationModule.moduleName

    if (!moduleName.isBlank()) {
        val moduleElement = Element(MODULE)
        moduleElement.setAttribute(NAME, moduleName)
        addContent(moduleElement)
    }
}

fun Element.readExternalModule(configuration: Configuration) {
    getChild(MODULE)?.getAttributeValue(NAME)?.let { moduleName ->
        configuration.configurationModule.module = configuration.configurationModule.findModule(moduleName)
    }
}

fun Element.writeExternalWorkingDirectory(workingDirectoryURL: String?) {
    if (!workingDirectoryURL.isNullOrBlank()) {
        val workingDirectoryElement = Element(WORKING_DIRECTORY)
        workingDirectoryElement.setAttribute(URL, workingDirectoryURL)
        addContent(workingDirectoryElement)
    }
}

fun Element.readExternalWorkingDirectory(): String? = getChild(WORKING_DIRECTORY)?.getAttributeValue(URL)

const val ELIXIR = "elixir"
const val ERL = "erl"
const val MIX = "mix"
private const val MODULE = "module"
private const val NAME = "name"
private const val URL = "url"
private const val WORKING_DIRECTORY = "working-directory"

abstract class Configuration(name: String, project: Project, configurationFactory: ConfigurationFactory) :
        ModuleBasedConfiguration<Module>(name, Module(project), configurationFactory),
        CommonProgramRunConfigurationParameters {
    override fun getEnvs(): Map<String, String> = _envs

    override fun setEnvs(envs: Map<String, String>) {
        _envs.clear()
        _envs.putAll(envs)
    }

    fun ensureModule(): com.intellij.openapi.module.Module =
            configurationModule.module ?: ensureWorkingDirectory().let { ensureModule(it, project) }

    override fun getWorkingDirectory(): String? = ExternalizablePath.localPathValue(workingDirectoryURL)

    fun ensureWorkingDirectory(): String {
        var workingDirectory = this.workingDirectory

        if (workingDirectory.isNullOrBlank()) {
            workingDirectory = ensureWorkingDirectory(project, configurationModule.module)
        }

        return workingDirectory!!
    }

    override fun setWorkingDirectory(localPath: String?) {
        workingDirectoryURL = ExternalizablePath.urlValue(localPath)
    }

    override fun isPassParentEnvs(): Boolean = _passParentEnvs

    override fun setPassParentEnvs(passParentEnvs: Boolean) {
        _passParentEnvs = passParentEnvs
    }

    override fun getValidModules(): Collection<com.intellij.openapi.module.Module> =
            project.let { ModuleManager.getInstance(it) }.modules.asList()

    private val _envs = mutableMapOf<String, String>()
    private var _passParentEnvs: Boolean = false
    protected var workingDirectoryURL: String? = null
}

private const val ARGUMENT = "argument"

private fun ensureModule(workingDirectory: String, project: Project): com.intellij.openapi.module.Module {
    val virtualFile = VfsUtil.findFileByIoFile(File(workingDirectory), true)
            ?: error("Working directory ($workingDirectory) could not be mapped to a VirtualFile")

    return ModuleUtilCore.findModuleForFile(virtualFile, project)
            ?: error("Working directory ($workingDirectory) VirtualFile could not be mapped to a Module")
}

private fun workingDirectory(module: com.intellij.openapi.module.Module): String? =
        ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path