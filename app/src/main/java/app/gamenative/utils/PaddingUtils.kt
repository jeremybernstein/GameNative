package app.gamenative.utils

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager
import app.gamenative.enums.StatusBarMode

object `PaddingUtils` {
    /**
     * Creates padding values with conditional top padding based on [StatusBarMode].
     * When mode is [StatusBarMode.HIDDEN], top padding is 0.dp; otherwise uses [defaultPadding].
     * All other sides always use [defaultPadding].
     */
    fun statusBarAwarePadding(
        defaultPadding: Dp = 16.dp
    ): PaddingValues {

        val hideStatusBar = PrefManager.statusBarMode == StatusBarMode.HIDDEN

        return PaddingValues(
            top = if (hideStatusBar) 0.dp else defaultPadding,
            start = defaultPadding,
            end = defaultPadding,
            bottom = defaultPadding
        )
    }
}
