package io.github.hyyz17200.tunnelgate

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle

object V2RayTunTaskerContract {
    const val PACKAGE_NAME = "com.v2raytun.android"
    const val TASKER_ACTIVITY_CLASS = "com.v2raytun.android.ui.activity.TaskerActivity"
    const val TASKER_RECEIVER_CLASS = "com.v2raytun.android.receiver.TaskerReceiver"

    const val ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"

    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"

    fun buildEditIntent(): Intent {
        return Intent(ACTION_EDIT_SETTING).apply {
            component = ComponentName(PACKAGE_NAME, TASKER_ACTIVITY_CLASS)
        }
    }

    fun buildFireIntent(bundle: Bundle, blurb: String): Intent {
        return Intent(ACTION_FIRE_SETTING).apply {
            component = ComponentName(PACKAGE_NAME, TASKER_RECEIVER_CLASS)
            setPackage(PACKAGE_NAME)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(EXTRA_BUNDLE, bundle)
            if (blurb.isNotBlank()) {
                putExtra(EXTRA_STRING_BLURB, blurb)
            }
        }
    }
}
