package com.ahmadns7.mercureal.assistant.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AssistantState {
    var label by mutableStateOf("idle")
    var transcript by mutableStateOf("")
    var reply by mutableStateOf("")
    /** True while a conversation session is open (waiting for or handling a follow-up). */
    var session by mutableStateOf(false)
}
