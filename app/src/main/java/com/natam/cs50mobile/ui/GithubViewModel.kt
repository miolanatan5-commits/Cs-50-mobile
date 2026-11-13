package com.natam.cs50mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.natam.cs50mobile.CS50App
import com.natam.cs50mobile.core.cs50.Cs50Setup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Liga o Linux ao GitHub (chave SSH) e dispara check50 e submit50 no terminal. */
class GithubViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CS50App).container
    private val session = container.mainSession

    private val _publicKey = MutableStateFlow<String?>(null)
    val publicKey: StateFlow<String?> = _publicKey.asStateFlow()

    init {
        refreshKey()
    }

    fun refreshKey() {
        viewModelScope.launch(Dispatchers.IO) {
            _publicKey.value = Cs50Setup.publicKey(container.paths)
        }
    }

    /** Gera a chave, configura o SSH pela porta 443 e o nome/e-mail do git. Roda no terminal. */
    fun setup(name: String, email: String) {
        dispatch(
            Cs50Setup.GITHUB_COMMAND + " setup " + Cs50Setup.shellQuote(name.trim()) + " " +
                Cs50Setup.shellQuote(email.trim()) + " " + Cs50Setup.lang()
        ) { refreshKey() }
    }

    fun testConnection() = dispatch(Cs50Setup.GITHUB_COMMAND + " test " + Cs50Setup.lang())

    /** [tool] é "check50" ou "submit50"; [folder] é relativa ao workspace. */
    fun runTool(tool: String, folder: String, slug: String) {
        val dir = if (folder.isBlank()) "/root/workspace" else "/root/workspace/" + folder.trim('/')
        dispatch("cd " + Cs50Setup.shellQuote(dir) + " && " + tool + " " + Cs50Setup.shellQuote(slug.trim()))
    }

    private fun dispatch(line: String, after: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Cs50Setup.writeScript(getApplication<Application>(), container.paths)
            }
            session.sendLine(line)
            after()
        }
    }

    companion object {
        private val SAFE_PATH = Regex("[A-Za-z0-9._/-]*")

        /** Pasta e slug vão para um comando de shell: só caracteres simples, sem "..". */
        fun isSafePath(value: String): Boolean =
            SAFE_PATH.matches(value) && !value.split('/').contains("..")
    }
}
