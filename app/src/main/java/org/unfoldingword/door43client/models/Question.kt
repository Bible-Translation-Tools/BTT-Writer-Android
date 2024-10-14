package org.unfoldingword.door43client.models;

/**
 * Represents a single question in a questionnaire
 */
public class Question {
    public final String text;
    public final String help;
    public final boolean isRequired;
    public final InputType inputType;
    public final int sort;
    /**
     * The question this one depends on. If -1 there is no dependency
     */
    public final long dependsOn;
    /**
     * This question's translation database id
     */
    public final long tdId;

    /**
     *
     * @param text the question
     * @param help optional help text
     * @param isRequired indicates if this question requires an answer
     * @param inputType the type of form input used to display this question e.g. input text, boolean
     * @param sort the sorting order of this question
     * @param dependsOn the translation database id of the question that this question depends on. Set as -1 for no dependency
     * @param tdId the translation database id of this question (server side)
     */
    public Question(String text, String help, boolean isRequired, InputType inputType, int sort, long dependsOn, long tdId) {
        this.text = text;
        this.help = help;
        this.isRequired = isRequired;
        this.inputType = inputType;
        this.sort = sort;
        this.dependsOn = dependsOn;
        this.tdId = tdId;
    }

    public enum InputType {
        String("string"),
        Boolean("boolean"),
        Date("date");

        InputType(String label) {
            this.label = label;
        }

        private final String label;

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return getLabel();
        }

        /**
         * Returns an input type by it's label
         * @param label
         * @return
         */
        public static InputType get(String label) {
            if(label != null) {
                for (InputType t : InputType.values()) {
                    if (t.getLabel().equals(label.toLowerCase())) {
                        return t;
                    }
                }
            }
            return null;
        }
    }
}
