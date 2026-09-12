package com.mrrob.llmchat.data

/**
 * The three prompt stages of Judge Mode: independent answers, the peer-review
 * round, and the judge's consolidated verdict. The judge is asked for fixed
 * section headings so the UI can split its verdict into the consensus body,
 * the disagreement note and the reasoning expander ([parseVerdict]).
 */
object JudgePrompts {

    /** Stable labels for the three seats so prompts and UI agree. */
    val labels = listOf("A", "B", "C")

    const val H_CONSENSUS = "## Consensus Answer"
    const val H_DISAGREED = "## Where the panel disagreed"
    const val H_REASONING = "## Why this answer"

    /** One model's turn to answer on its own, with no knowledge of the others. */
    fun analystSystem(label: String): String =
        "You are Analyst $label in a panel of three independent AI analysts answering the " +
            "same question for a user. Give your best single answer: direct, accurate and " +
            "complete, with the key reasoning behind your conclusion visible. " +
            "Do not mention the other analysts and do not ask for clarification."

    /** One model's turn to critique the whole panel's answers, including its own. */
    fun reviewSystem(label: String): String =
        "You are Analyst $label in a panel of three AI analysts. You have just read all " +
            "three answers to the same question. Write a tight peer review of the panel, " +
            "using exactly these four lines, one per line, each starting with the label " +
            "and a colon:\n" +
            "Agreements: where the answers concur and why that is likely solid.\n" +
            "Disagreements: every factual or judgemental conflict between them.\n" +
            "Weak points: unsupported claims, missing considerations or errors, " +
            "including in your own answer.\n" +
            "Strongest material: the specific parts worth keeping in a final answer.\n" +
            "Be concrete and name which answers (A, B, C) you mean. Do not re-answer the question."

    /** The judge's turn: weigh everything and produce the one final answer. */
    fun judgeSystem(): String =
        "You are the Judge of a three-analyst panel. You have the question, all three " +
            "independent answers and the peer reviews. Decide the panel's final answer and " +
            "produce it in exactly this structure, using these three headings and no others:\n" +
            "$H_CONSENSUS\n" +
            "The single best answer, combining only the accurate, useful and well-supported " +
            "material. Resolve disagreements explicitly rather than averaging them away.\n" +
            "$H_DISAGREED\n" +
            "One short paragraph on the real conflicts left over and which side you ruled " +
            "for, with the reason.\n" +
            "$H_REASONING\n" +
            "The decisive evidence and the main caveats or confidence limits.\n" +
            "Write for the person who asked the question, not for the panel."

    /** User-side message for the review round: question plus the three answers. */
    fun reviewUser(question: String, answers: List<Pair<String, String>>): String = buildString {
        append("Question: ").append(question).append("\n\n")
        append("The three answers are:")
        answers.forEach { (label, text) ->
            append("\n\n--- Answer by Analyst ").append(label).append(" ---\n").append(text.trim())
        }
        append("\n\nWrite your peer review of the panel.")
    }

    /** User-side message for the judge round: everything the panel produced. */
    fun judgeUser(
        question: String,
        answers: List<Pair<String, String>>,
        reviews: List<Pair<String, String>>
    ): String = buildString {
        append("Question: ").append(question).append("\n\n")
        append("INDEPENDENT ANSWERS")
        answers.forEach { (label, text) ->
            append("\n\n[Analyst ").append(label).append("] ").append(text.trim())
        }
        append("\n\nPEER REVIEWS")
        reviews.forEach { (label, text) ->
            append("\n\n[Review by Analyst ").append(label).append("] ").append(text.trim())
        }
        append("\n\nDeliver the panel's final answer.")
    }

    /**
     * Compact context block for follow-up turns: the earlier questions and the
     * panel's consensus, so the analysts stay coherent across a session.
     */
    fun contextBlock(prior: List<Pair<String, String>>): String = buildString {
        append("EARLIER IN THIS CONVERSATION (for context only):\n")
        prior.forEach { (question, consensus) ->
            append("\nQ: ").append(question)
                .append("\nPanel consensus so far: ").append(consensus.trim())
                .append('\n')
        }
        append("\nNow answer the NEW question that follows, using the context above where relevant.")
    }

    /** The judge's verdict split into its three parts; missing sections fall back gracefully. */
    fun parseVerdict(text: String): Triple<String, String, String> {
        fun section(start: String, end: String?): String =
            text.substringAfter(start, "").substringBefore(end ?: "\u0000").trim()
        val consensus = section(H_CONSENSUS, H_DISAGREED)
        val disagreed = section(H_DISAGREED, H_REASONING)
        val reasoning = section(H_REASONING, null)
        return if (consensus.isBlank() && disagreed.isBlank() && reasoning.isBlank()) {
            Triple(text.trim(), "", "")
        } else {
            Triple(consensus.ifBlank { text.trim() }, disagreed, reasoning)
        }
    }
}
