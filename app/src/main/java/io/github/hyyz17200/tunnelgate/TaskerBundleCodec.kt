package io.github.hyyz17200.tunnelgate

import android.os.Bundle
import android.os.Parcel
import android.util.Base64

object TaskerBundleCodec {
    fun encode(bundle: Bundle?): String {
        if (bundle == null) return ""
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
        } finally {
            parcel.recycle()
        }
    }

    fun decode(encoded: String): Bundle? {
        if (encoded.isBlank()) return null
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            @Suppress("DEPRECATION")
            parcel.readBundle(TaskerBundleCodec::class.java.classLoader)
        } finally {
            parcel.recycle()
        }
    }

    fun summarize(bundle: Bundle?): String {
        if (bundle == null) return "Bundle: not configured"
        val keys = bundle.keySet().sorted()
        if (keys.isEmpty()) return "Bundle: empty"
        return keys.joinToString(prefix = "Bundle keys: ", separator = ", ")
    }
}
