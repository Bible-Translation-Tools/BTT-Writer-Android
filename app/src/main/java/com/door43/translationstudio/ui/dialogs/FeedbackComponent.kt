package com.door43.translationstudio.ui.dialogs

import com.door43.translationstudio.core.Progress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface FeedbackComponent {
    val state: StateFlow<FeedbackState>
    val progress: StateFlow<Progress?>
    val event: Flow<FeedbackEvent>

    fun onAction(action: FeedbackAction)
}
