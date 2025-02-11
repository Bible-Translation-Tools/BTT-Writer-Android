package com.door43.questionnaire

import org.unfoldingword.door43client.models.Question

/**
 * Represents a page of questions in questionnaire pager
 */
class QuestionnairePage {
    private val questions: MutableMap<Long, Question> = LinkedHashMap()

    /**
     * Adds a question to this page
     * @param question
     */
    fun addQuestion(question: Question) {
        questions[question.tdId] = question
    }

    /**
     * Checks if this page contains the question
     * @param id
     * @return
     */
    fun containsQuestion(id: Long): Boolean {
        return questions.containsKey(id)
    }

    /**
     * Returns the questions on this page
     * @return
     */
    fun getQuestions(): List<Question> {
        return ArrayList(questions.values)
    }

    val numQuestions: Int
        /**
         * Returns the number of questions on this page
         * @return
         */
        get() = questions.size

    /**
     * Returns the question on this page by position
     * @param position
     * @return
     */
    fun getQuestion(position: Int): Question? {
        if (position in 0 until numQuestions) {
            return getQuestions()[position]
        }
        return null
    }

    /**
     * Returns the question on this page by id
     * @param id
     * @return
     */
    fun getQuestionById(id: Long): Question? {
        return questions[id]
    }

    /**
     * Returns the index of the question
     * @param questionId
     * @return
     */
    fun indexOf(questionId: Long): Int {
        return ArrayList(questions.keys).indexOf(questionId)
    }

    /**
     * Removes a question from the page
     * @param questionId
     */
    fun removeQuestion(questionId: Long) {
        questions.remove(questionId)
    }
}
