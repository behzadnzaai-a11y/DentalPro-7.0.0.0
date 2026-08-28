package com.example.ui.theme

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** App-wide SnackbarHostState, provided once at the root Scaffold in
 * MainDentalScreen so any nested screen can show an Undo snackbar without
 * prop-drilling a SnackbarHostState through every intermediate composable. */
val LocalSnackbarHostState = compositionLocalOf { SnackbarHostState() }

/**
 * Shows a short "X deleted" snackbar with an Undo action. If the user taps
 * Undo before it dismisses, [onUndo] restores the item; otherwise nothing
 * further happens (the delete already went through).
 */
fun showUndoSnackbar(
    scope: CoroutineScope,
    hostState: SnackbarHostState,
    message: String,
    actionLabel: String,
    onUndo: () -> Unit
) {
    scope.launch {
        val result = hostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndo()
        }
    }
}
