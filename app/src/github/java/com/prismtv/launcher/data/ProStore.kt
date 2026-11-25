package com.prismtv.launcher.data

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** GitHub edition: Pro is always on, so there's nothing to buy. (The Play edition's is in src/play.) */
object ProStore {
    val price: StateFlow<String?> = MutableStateFlow(null)
    /** Bumped whenever ownership may have changed. */
    val changes: StateFlow<Int> = MutableStateFlow(0)
    fun start(ctx: Context) = Unit
    fun refresh() = Unit
    fun buy(activity: Activity): Boolean = false
}
