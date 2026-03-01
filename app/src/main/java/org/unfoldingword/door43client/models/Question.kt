package org.unfoldingword.door43client.models

import java.util.Locale

/**
 * Represents a single question in a questionnaire
 */
data class Question(
    /** the question */
    val text: String,
    /** optional help text */
    val help: String,
    /** indicates if this question requires an answer */
    val isRequired: Boolean,
    /** the type of form input used to display this question e.g. input text, boolean */
    val inputType: InputType,
    /** the sorting order of this question */
    val sort: Int,
    /** the translation database id of the question that this question depends on. Set as -1 for no dependency */
    val dependsOn: Long,
    /** the translation database id of this question (server side) */
    val tdId: Long
) {
    enum class InputType(val label: String) {
        String("string"),
        Boolean("boolean"),
        Date("date");

        override fun toString(): String {
            return label
        }

        companion object {
            /**
             * Returns an input type by its label
             * @param label
             * @return
             */
            fun get(label: String): InputType {
                for (t in entries) {
                    if (t.label == label.lowercase(Locale.getDefault())) {
                        return t
                    }
                }
                throw IllegalArgumentException("Unknown input type: $label")
            }
        }
    }
}
