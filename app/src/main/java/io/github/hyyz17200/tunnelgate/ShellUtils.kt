package io.github.hyyz17200.tunnelgate

import java.io.BufferedReader

object ShellUtils {
    data class Result(
        val code: Int,
        val stdout: String,
        val stderr: String,
    )

    fun execRoot(command: String): Result {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
        val code = process.waitFor()
        return Result(code = code, stdout = stdout, stderr = stderr)
    }

    fun checkSu(): Result {
        return runCatching { execRoot("id") }
            .getOrElse { Result(code = -1, stdout = "", stderr = it.message ?: "unknown error") }
    }
}
