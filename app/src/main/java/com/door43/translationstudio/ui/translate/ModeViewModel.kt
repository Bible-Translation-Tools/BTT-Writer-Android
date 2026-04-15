package com.door43.translationstudio.ui.translate

import androidx.lifecycle.ViewModel
import com.door43.translationstudio.core.TranslationViewMode
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.StateFlow


abstract class ModeViewModel<ITEM: TranslateItem>(
    protected val sharedState: StateFlow<TranslateComponent.SharedState>,
    protected val eventSender: SendChannel<TranslateComponent.Event>,
    protected val mode: TranslationViewMode,
) : ViewModel() {






}
