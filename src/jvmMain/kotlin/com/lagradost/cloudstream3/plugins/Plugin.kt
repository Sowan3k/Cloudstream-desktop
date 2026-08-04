package com.lagradost.cloudstream3.plugins

import android.content.Context

/**
 * Minimal JVM stub for CloudStream Plugin class.
 */
abstract class Plugin : BasePlugin() {
    open fun load(context: Context) {}
}
