package com.agcodespace.codespaces

object CodespaceCommands {
    const val AUTH = "gh auth login --web"
    const val LIST = "gh codespace list"
    fun create(repo: String, branch: String? = null): String = buildString {
        append("gh codespace create -r ").append(shell(repo))
        if (!branch.isNullOrBlank()) append(" -b ").append(shell(branch))
    }
    fun ssh(name: String): String = "gh codespace ssh -c ${shell(name)}"
    fun shell(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
