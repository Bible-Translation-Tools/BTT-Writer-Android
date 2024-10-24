package com.door43.questionnaire

import org.unfoldingword.door43client.models.Question
import org.unfoldingword.door43client.models.Questionnaire

/**
 * paginates a questionnaire
 */
class QuestionnairePager(val questionnaire: Questionnaire) {
    private var pages: MutableList<QuestionnairePage> = ArrayList()

    /**
     * Checks if a data field exists.
     *
     * @param key
     * @return
     */
    fun hasDataField(key: String): Boolean {
        return questionnaire.dataFields.containsKey(key)
    }

    /**
     * Returns the tdID of the question link to this data field
     * @param key
     * @return
     */
    fun getDataField(key: String): Long? {
        return questionnaire.dataFields[key]
    }

    /**
     * Loads the questions in pages
     * @param questions these questions should already be sorted correctly
     */
    fun loadQuestions(questions: List<Question>) {
        pages = ArrayList()
        val questionsAdded: MutableList<Long> = ArrayList()
        var currentPage = QuestionnairePage()

        for (q in questions) {
            val inCurrentPage = currentPage.containsQuestion(q.dependsOn)
            val questionAdded = questionsAdded.contains(q.tdId)
            val currentQuestion = currentPage.getQuestionById(q.dependsOn)
            val currentIndex = currentPage.indexOf(q.dependsOn)

            when {
                !inCurrentPage && currentPage.numQuestions >= QUESTIONS_PER_PAGE -> {
                    // close full page
                    pages.add(currentPage)
                    currentPage = QuestionnairePage()
                }
                !inCurrentPage && questionAdded -> {
                    // add out of order question to correct page
                    var placedQuestion = false
                    for (processedPage in pages) {
                        if (processedPage.containsQuestion(q.dependsOn)) {
                            processedPage.addQuestion(q)
                            placedQuestion = true
                            break
                        }
                    }
                    if (placedQuestion) {
                        questionsAdded.add(q.tdId)
                        continue
                    }
                }
                inCurrentPage && currentQuestion != null && currentQuestion.dependsOn < 0 && currentIndex > 0 -> {
                    // place non-dependent reliant question in it's own page
                    currentPage.removeQuestion(q.dependsOn)

                    // close page
                    pages.add(currentPage)
                    currentPage = QuestionnairePage()

                    // add questions to page
                    currentPage.addQuestion(currentQuestion)
                    currentPage.addQuestion(q)
                    questionsAdded.add(q.tdId)
                    continue
                }
            }

            // add question to page
            currentPage.addQuestion(q)
            questionsAdded.add(q.tdId)
        }
        if (currentPage.numQuestions > 0) {
            pages.add(currentPage)
        }
    }

    /**
     * Returns the number of pages
     * @return
     */
    fun size(): Int {
        return pages.size
    }

    /**
     * Returns a page by its position
     * @param position
     * @return
     */
    fun getPage(position: Int): QuestionnairePage? {
        if (position >= 0 && position < size()) {
            return pages[position]
        }
        return null
    }

    /**
     * Returns a question if found in this pager
     * @param tdId
     * @return
     */
    fun getQuestion(tdId: Long): Question? {
        for (page in pages) {
            for (question in page.getQuestions()) {
                if (question.tdId == tdId) return question
            }
        }
        return null
    }

    companion object {
        private const val QUESTIONS_PER_PAGE = 3
    }
}
