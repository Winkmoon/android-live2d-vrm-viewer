package com.winkmoon.xinran

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * State bridge between a renderer content and the single shared viewer shell.
 * The shell owns the top bar, bottom bar, stats card and settings sheet; the
 * content owns only the GL/WebView surface and its model/motion sheets.
 */
class Live2DContentState {
    var stats by mutableStateOf(PerfStats())
    var modelName by mutableStateOf("")

    var openModels: (() -> Unit)? = null
    var openMotions: (() -> Unit)? = null
    var importModel: (() -> Unit)? = null
    var reloadModel: (() -> Unit)? = null
    var dismissSheets: (() -> Unit)? = null

    var rootAvailable by mutableStateOf(false)
    var rootEnabled by mutableStateOf(false)
    var rootBusy by mutableStateOf(false)
    var rootStatus by mutableStateOf<String?>(null)
    var toggleRoot: ((Boolean) -> Unit)? = null
}

class Avatar3DContentState {
    var stats by mutableStateOf(Avatar3DStats())
    var name by mutableStateOf("")

    var openModels: (() -> Unit)? = null
    var importAvatar: (() -> Unit)? = null
    var resetView: (() -> Unit)? = null
    var dismissSheets: (() -> Unit)? = null
}
