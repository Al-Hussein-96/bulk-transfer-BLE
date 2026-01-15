package com.alhussain.bulk_transfer.presentation.components

sealed class Screen {
    object ModeSelection : Screen()
    object Sender : Screen()
    object Receiver : Screen()
}
