package io.github.hyyz17200.tunnelgate

object AppConst {
    const val APPLICATION_ID = "io.github.hyyz17200.tunnelgate"

    const val ACTION_CONTROL = APPLICATION_ID + ".ACTION_CONTROL"
    const val ACTION_REEVALUATE = APPLICATION_ID + ".ACTION_REEVALUATE"
    const val ACTION_QUERY_HOOK_STATUS = APPLICATION_ID + ".ACTION_QUERY_HOOK_STATUS"
    const val ACTION_HOOK_STATUS = APPLICATION_ID + ".ACTION_HOOK_STATUS"
    const val ACTION_LOG_EVENT = APPLICATION_ID + ".ACTION_LOG_EVENT"

    const val RECEIVER_CLASS = APPLICATION_ID + ".ControlReceiver"
    const val EVENT_RECEIVER_CLASS = APPLICATION_ID + ".AppEventReceiver"
}
