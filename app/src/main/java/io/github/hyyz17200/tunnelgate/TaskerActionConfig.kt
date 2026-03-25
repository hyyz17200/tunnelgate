package io.github.hyyz17200.tunnelgate

data class TaskerActionConfig(
    val bundleBase64: String = "",
    val blurb: String = "",
) {
    fun isConfigured(): Boolean = bundleBase64.isNotBlank()
}
