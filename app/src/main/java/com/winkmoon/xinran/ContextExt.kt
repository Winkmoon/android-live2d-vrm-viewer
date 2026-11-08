package com.winkmoon.xinran

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Compose's LocalContext is often a ContextThemeWrapper, so a plain
 * `context as? Activity` is not reliable.
 */
tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
