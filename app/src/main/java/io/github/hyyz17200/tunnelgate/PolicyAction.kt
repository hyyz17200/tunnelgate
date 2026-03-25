package io.github.hyyz17200.tunnelgate

enum class PolicyAction {
    START,
    STOP;

    fun isEnabled(): Boolean = this == START
}
