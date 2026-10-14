package com.nexus.control

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ShellResult(
    val command: String,
    val exitCode: Int?,
    val output: String,
    val timedOut: Boolean
)

object LocalShell {
    private const val MAX_OUTPUT_CHARS = 64 * 1024

    suspend fun run(command: String, timeoutSeconds: Long = 10): ShellResult = withContext(Dispatchers.IO) {
        val clean = command.trim().take(4000)
        if (clean.isBlank()) return@withContext ShellResult("", null, "Komut boş", false)

        runCatching {
            val process = ProcessBuilder("/system/bin/sh", "-c", clean)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                buildString {
                    var line: String?
                    while (reader.readLine().also { line = it } != null && length < MAX_OUTPUT_CHARS) {
                        appendLine(line)
                    }
                }.take(MAX_OUTPUT_CHARS)
            }
            ShellResult(
                command = clean,
                exitCode = if (completed) process.exitValue() else null,
                output = output.ifBlank { if (completed) "(çıktı yok)" else "Komut zaman aşımına uğradı" },
                timedOut = !completed
            )
        }.getOrElse { ShellResult(clean, null, "Hata: ${it.message ?: it.javaClass.simpleName}", false) }
    }
}
