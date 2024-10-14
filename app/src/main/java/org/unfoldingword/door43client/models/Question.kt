package org.unfoldingword.door43client.models

import java.util.Locale

/**
 * Represents a single question in a questionnaire
 */
data class Question(
    /** the question */
    @JvmField val text: String,
    /** optional help text */
    @JvmField val help: String,
    /** indicates if this question requires an answer */
    @JvmField val isRequired: Boolean,
    /** the type of form input used to display this question e.g. input text, boolean */
    @JvmField val inputType: InputType,
    /** the sorting order of this question */
    @JvmField val sort: Int,
    /** the translation database id of the question that this question depends on. Set as -1 for no dependency */
    @JvmField val dependsOn: Long,
    /** the translation database id of this question (server side) */
    @JvmField val tdId: Long
) {
    enum class InputType(val label: kotlin.String) {
        String("string"),
        Boolean("boolean"),
        Date("date");

        override fun toString(): kotlin.String {
            return label
        }

        companion object {
            /**
             * Returns an input type by it's label
             * @param label
             * @return
             */
            @JvmStatic
            fun get(label: kotlin.String?): InputType? {
                if (label != null) {
                    for (t in entries) {
                        if (t.label == label.lowercase(Locale.getDefault())) {
                            return t
                        }
                    }
                }
                return null
            }
        }
    }
}
