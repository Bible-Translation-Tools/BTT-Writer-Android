package com.door43.translationstudio.ui.translate

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import com.door43.translationstudio.databinding.FragmentReviewListItemBinding
import com.door43.translationstudio.databinding.FragmentReviewListItemMergeConflictBinding
import com.door43.widget.LinedEditText
import com.google.android.material.tabs.TabLayout

interface IReviewListItemBinding {
    val root: View
    val mainContent: LinearLayout
    val sourceLoader: LinearLayout
    val sourceCard: CardView
    val sourceBody: TextView
    val resourceCard: CardView
    val resourceLayout: LinearLayout
    val resourceTabs: TabLayout
    val resourceList: LinearLayout
    val targetCard: CardView
    val targetInnerCard: LinearLayout
    val targetTitle: TextView
    val targetBody: EditText?
    val targetEditableBody: LinedEditText?
    val translationTabs: TabLayout
    val editButton: ImageButton?
    val addNoteButton: ImageButton?
    val undoButton: ImageButton?
    val redoButton: ImageButton?
    val doneSwitch: SwitchCompat?
    val newTabButton: ImageButton
    val mergeConflictLayout: LinearLayout?
    val conflictText: TextView?
    val buttonBar: LinearLayout?
    val cancelButton: Button?
    val confirmButton: Button?
}

class ReviewListItemBinding(
    val binding: FragmentReviewListItemBinding
) : IReviewListItemBinding {
    override val root = binding.root
    override val mainContent = binding.mainContent
    override val sourceLoader = binding.itemSource.sourceLoader
    override val sourceCard = binding.itemSource.sourceTranslationCard
    override val sourceBody = binding.itemSource.sourceTranslationBody
    override val resourceCard = binding.itemResources.resourcesCard
    override val resourceLayout = binding.itemResources.resourcesLayout
    override val resourceTabs = binding.itemResources.resourceTabs
    override val resourceList = binding.itemResources.resourcesList
    override val targetCard = binding.itemTarget.targetTranslationCard
    override val targetInnerCard = binding.itemTarget.targetTranslationInnerCard
    override val targetTitle = binding.itemTarget.targetTranslationTitle
    override val targetBody = binding.itemTarget.targetTranslationBody
    override val targetEditableBody = binding.itemTarget.targetTranslationEditableBody
    override val translationTabs = binding.itemSource.sourceTranslationTabs
    override val editButton = binding.itemTarget.editTranslationButton
    override val addNoteButton = binding.itemTarget.addNoteButton
    override val undoButton = binding.itemTarget.undoButton
    override val redoButton = binding.itemTarget.redoButton
    override val doneSwitch = binding.itemTarget.doneButton
    override val newTabButton = binding.itemSource.newTabButton
    override val mergeConflictLayout = null
    override val conflictText = null
    override val buttonBar = null
    override val cancelButton = null
    override val confirmButton = null
}

class ReviewListItemMergeConflictBinding(
    val binding: FragmentReviewListItemMergeConflictBinding
) : IReviewListItemBinding {
    override val root = binding.root
    override val mainContent = binding.mainContent
    override val sourceLoader = binding.itemSource.sourceLoader
    override val sourceCard = binding.itemSource.sourceTranslationCard
    override val sourceBody = binding.itemSource.sourceTranslationBody
    override val resourceCard = binding.itemResources.resourcesCard
    override val resourceLayout = binding.itemResources.resourcesLayout
    override val resourceTabs = binding.itemResources.resourceTabs
    override val resourceList = binding.itemResources.resourcesList
    override val targetCard = binding.targetTranslationCard
    override val targetInnerCard = binding.targetTranslationInnerCard
    override val targetTitle = binding.targetTranslationTitle
    override val targetBody = null
    override val targetEditableBody = null
    override val translationTabs = binding.itemSource.sourceTranslationTabs
    override val editButton = null
    override val addNoteButton = null
    override val undoButton = null
    override val redoButton = null
    override val doneSwitch = null
    override val newTabButton = binding.itemSource.newTabButton
    override val mergeConflictLayout = binding.mergeCards
    override val conflictText = binding.conflictLabel
    override val buttonBar = binding.buttonBar
    override val cancelButton = binding.cancelButton
    override val confirmButton = binding.confirmButton
}