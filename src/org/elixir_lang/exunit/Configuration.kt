package org.elixir_lang.exunit

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.elixir_lang.ExUnit
import org.elixir_lang.Level.V_1_4
import org.elixir_lang.exunit.configuration.Editor
import org.elixir_lang.exunit.configuration.Factory
import org.elixir_lang.file.LevelPropertyPusher.level
import org.elixir_lang.mix.ensureMostSpecificSdk
import org.elixir_lang.run.*
import org.elixir_lang.run.Configuration
import org.elixir_lang.sdk.elixir.Type.mostSpecificSdk
import org.jdom.Element

class Configuration(name: String, project: Project) :
        Configuration(name, project, Factory),
        RunConfigurationWithSuppressedDefaultRunAction,
        RunConfigurationWithSuppressedDefaultDebugAction {
    val task
        get() =
            if (level(sdk()) >= V_1_4) {
                "test"
            } else {
                "test_with_formatter"
            }

    private fun sdk(): Sdk? {
        val module = configurationModule.module

        return if (module != null) {
            mostSpecificSdk(module)
        } else {
            mostSpecificSdk(project)
        }
    }

    override fun getProgramParameters(): String? = mixTestArguments

    override fun setProgramParameters(value: String?) {
        mixTestArguments = value
    }

    private var erlArgumentList: MutableList<String> = mutableListOf()

    var erlArguments: String?
        get() = erlArgumentList.toArguments()
        set(arguments) = erlArgumentList.fromArguments(arguments)

    private var elixirArgumentList: MutableList<String> = mutableListOf()

    var elixirArguments: String?
        get() = elixirArgumentList.toArguments()
        set(arguments) = elixirArgumentList.fromArguments(arguments)

    private var mixTestArguments: String?
        get() = mixTestArgumentList.toArguments()
        set(arguments) = mixTestArgumentList.fromArguments(arguments)

    private var mixTestArgumentList: MutableList<String> = mutableListOf()

    fun commandLine(): GeneralCommandLine {
        val workingDirectory = ensureWorkingDirectory()
        val module = ensureModule()
        val sdk = ensureMostSpecificSdk(module)
        val commandLine = ExUnit.commandLine(envs, workingDirectory, sdk, erlArgumentList, elixirArgumentList)
        commandLine.addParameters(mixTestArgumentList)

        return commandLine
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = Editor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
            State(environment, this)

    override fun readExternal(element: Element) {
        super.readExternal(element)
        element.readExternalArgumentList(ERL, erlArgumentList)
        element.readExternalArgumentList(ELIXIR, elixirArgumentList)
        element.readExternalArgumentList(MIX_TEST, mixTestArgumentList)
        workingDirectoryURL = element.readExternalWorkingDirectory()
        EnvironmentVariablesComponent.readExternal(element, envs)
        element.readExternalModule(this)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeExternalArgumentList(ERL, erlArgumentList)
        element.writeExternalArgumentList(ELIXIR, elixirArgumentList)
        element.writeExternalArgumentList(MIX_TEST, mixTestArgumentList)
        element.writeExternalWorkingDirectory(workingDirectoryURL)
        EnvironmentVariablesComponent.writeExternal(element, envs)
        element.writeExternalModule(this)
    }
}

private const val MIX_TEST = "mix-test"