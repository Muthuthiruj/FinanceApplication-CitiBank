package com.example.financeapplication.core

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** One-shot events emitted by view models. */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    /** The requested operation finished successfully. */
    data class Done(val message: String? = null) : UiEvent
    data class FileReady(val file: java.io.File) : UiEvent
}

abstract class BaseViewModel : ViewModel() {
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    protected fun message(text: String) { _events.trySend(UiEvent.Message(text)) }
    protected fun done(text: String? = null) { _events.trySend(UiEvent.Done(text)) }
    protected fun emit(event: UiEvent) { _events.trySend(event) }
}
