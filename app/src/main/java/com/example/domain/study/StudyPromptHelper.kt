package com.example.domain.study

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object StudyPromptHelper {

    fun buildDoubtPrompt(
        subject: String,
        chapter: String,
        topic: String,
        studentQuestion: String,
        attachedNotesOrDoc: String? = null,
        language: String = "English"
    ): String {
        return buildString {
            appendLine("Subject: $subject")
            appendLine("Chapter: $chapter")
            appendLine("Topic: $topic")
            appendLine("Language: $language")
            if (!attachedNotesOrDoc.isNullOrBlank()) {
                appendLine("Student Attached Reference/Context:")
                appendLine(attachedNotesOrDoc.trim())
            }
            appendLine("Student Question:")
            appendLine(studentQuestion.trim())
            appendLine()
            appendLine("Please provide a pedagogical solution containing the following sections clearly labeled:")
            appendLine("1. Direct Answer: (Concise, immediate answer)")
            appendLine("2. Simple Explanation: (Easy to grasp intuitive breakdown)")
            appendLine("3. Step-by-Step Solution: (Detailed mathematical or conceptual sequence)")
            appendLine("4. Important Concept: (The core syllabus rule or formula)")
            appendLine("5. Common Mistake: (Pitfalls students frequently make)")
            appendLine("6. Similar Practice Question: (One fresh practice question with brief hint)")
            appendLine()
            appendLine("Note: If the question or attached context is ambiguous, state what is uncertain and how you interpreted it.")
        }
    }

    fun parseDoubtResponse(raw: String): DoubtSolution {
        val lines = raw.lines()
        var currentSection = ""
        val sections = mutableMapOf<String, StringBuilder>()

        for (line in lines) {
            val trimmed = line.trim()
            val lower = trimmed.lowercase()
            when {
                lower.startsWith("1.") || lower.contains("direct answer") -> {
                    currentSection = "direct"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("direct") { StringBuilder() }.appendLine(content.trim())
                }
                lower.startsWith("2.") || lower.contains("simple explanation") -> {
                    currentSection = "simple"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("simple") { StringBuilder() }.appendLine(content.trim())
                }
                lower.startsWith("3.") || lower.contains("step-by-step") || lower.contains("step by step") -> {
                    currentSection = "steps"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("steps") { StringBuilder() }.appendLine(content.trim())
                }
                lower.startsWith("4.") || lower.contains("important concept") -> {
                    currentSection = "concept"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("concept") { StringBuilder() }.appendLine(content.trim())
                }
                lower.startsWith("5.") || lower.contains("common mistake") -> {
                    currentSection = "mistake"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("mistake") { StringBuilder() }.appendLine(content.trim())
                }
                lower.startsWith("6.") || lower.contains("similar practice") -> {
                    currentSection = "practice"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("practice") { StringBuilder() }.appendLine(content.trim())
                }
                currentSection.isNotEmpty() -> {
                    sections.getOrPut(currentSection) { StringBuilder() }.appendLine(line)
                }
            }
        }

        return DoubtSolution(
            directAnswer = sections["direct"]?.toString()?.trim()?.ifBlank { "Answer provided in detailed explanation." } ?: "Answer provided in detailed explanation.",
            simpleExplanation = sections["simple"]?.toString()?.trim()?.ifBlank { raw } ?: raw,
            stepByStepSolution = sections["steps"]?.toString()?.trim() ?: "Review the detailed explanation steps above.",
            importantConcept = sections["concept"]?.toString()?.trim() ?: "Core curriculum concept for $currentSection.",
            commonMistake = sections["mistake"]?.toString()?.trim() ?: "Watch out for calculation or sign errors.",
            similarPracticeQuestion = sections["practice"]?.toString()?.trim() ?: "Practice another problem on this chapter to solidify the concept.",
            rawText = raw
        )
    }

    fun buildExplainTopicPrompt(
        subject: String,
        chapter: String,
        topic: String,
        difficulty: String,
        language: String
    ): String {
        return buildString {
            appendLine("Act as an expert CBSE Class 10 tutor.")
            appendLine("Explain the following curriculum topic:")
            appendLine("Subject: $subject")
            appendLine("Chapter: $chapter")
            appendLine("Topic: $topic")
            appendLine("Target Difficulty: $difficulty")
            appendLine("Language: $language")
            appendLine()
            appendLine("Structure your explanation as follows:")
            appendLine("## 1. Overview & Definition")
            appendLine("## 2. Intuitive Real-World Analogy")
            appendLine("## 3. Core Principles & Formulas (if applicable)")
            appendLine("## 4. Key Exam Points & Scoring Tips")
            appendLine("## 5. Quick Revision Summary")
        }
    }

    fun buildMCQPrompt(
        subject: String,
        chapter: String,
        topic: String,
        count: Int,
        difficulty: String,
        language: String
    ): String {
        return buildString {
            appendLine("Generate $count multiple-choice questions (MCQs) for CBSE Class 10.")
            appendLine("Subject: $subject")
            appendLine("Chapter: $chapter")
            appendLine("Topic: $topic")
            appendLine("Difficulty: $difficulty")
            appendLine("Language: $language")
            appendLine()
            appendLine("Output strictly valid JSON with no markdown backticks, conforming to this exact schema:")
            appendLine("[")
            appendLine("  {")
            appendLine("    \"question\": \"Question text here\",")
            appendLine("    \"options\": [\"Option A\", \"Option B\", \"Option C\", \"Option D\"],")
            appendLine("    \"correctIndex\": 0,")
            appendLine("    \"explanation\": \"Step-by-step why Option A is correct.\",")
            appendLine("    \"difficulty\": \"$difficulty\",")
            appendLine("    \"topic\": \"$topic\"")
            appendLine("  }")
            appendLine("]")
        }
    }

    fun parseMCQs(raw: String, fallbackTopic: String = "Curriculum Topic", fallbackDifficulty: String = "Medium"): List<StudyMCQ> {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonStart = cleaned.indexOf('[')
        val jsonEnd = cleaned.lastIndexOf(']')

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            val jsonSubstring = cleaned.substring(jsonStart, jsonEnd + 1)
            try {
                val array = JSONArray(jsonSubstring)
                val list = mutableListOf<StudyMCQ>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val q = obj.optString("question", "").ifBlank { obj.optString("question_text", "") }
                    if (q.isBlank()) continue

                    val optsArray = obj.optJSONArray("options")
                    val opts = mutableListOf<String>()
                    if (optsArray != null) {
                        for (j in 0 until optsArray.length()) {
                            val opt = optsArray.optString(j, "").trim()
                            if (opt.isNotBlank()) opts.add(opt)
                        }
                    }

                    if (opts.size < 2) continue
                    while (opts.size < 4) {
                        opts.add("Option ${opts.size + 1}")
                    }

                    // Resolve correct index from correctIndex or correct_answer
                    var correctIdx = obj.optInt("correctIndex", -1)
                    if (correctIdx == -1) {
                        correctIdx = obj.optInt("correct_index", -1)
                    }
                    if (correctIdx == -1) {
                        val corrStr = obj.optString("correct_answer", "").ifBlank { obj.optString("correctAnswer", "") }.trim()
                        correctIdx = when {
                            corrStr.equals("A", ignoreCase = true) || corrStr.startsWith("A)", true) -> 0
                            corrStr.equals("B", ignoreCase = true) || corrStr.startsWith("B)", true) -> 1
                            corrStr.equals("C", ignoreCase = true) || corrStr.startsWith("C)", true) -> 2
                            corrStr.equals("D", ignoreCase = true) || corrStr.startsWith("D)", true) -> 3
                            else -> {
                                val match = opts.indexOfFirst { it.equals(corrStr, ignoreCase = true) }
                                if (match != -1) match else 0
                            }
                        }
                    }
                    correctIdx = correctIdx.coerceIn(0, (opts.size - 1).coerceAtLeast(0))

                    val exp = obj.optString("explanation", "").ifBlank { "Verified concept based on CBSE Class 10 syllabus." }
                    val diff = obj.optString("difficulty", "").ifBlank { fallbackDifficulty }
                    val top = obj.optString("topic", "").ifBlank { fallbackTopic }

                    list.add(
                        StudyMCQ(
                            id = UUID.randomUUID().toString(),
                            question = q,
                            options = opts,
                            correctIndex = correctIdx,
                            correctAnswer = opts.getOrElse(correctIdx) { opts[0] },
                            explanation = exp,
                            difficulty = diff,
                            topic = top
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }

        // Fallback parser if raw response is markdown formatted
        return parseMCQFromMarkdown(raw, fallbackTopic, fallbackDifficulty)
    }

    private fun parseMCQFromMarkdown(raw: String, fallbackTopic: String, fallbackDifficulty: String): List<StudyMCQ> {
        val lines = raw.lines()
        val list = mutableListOf<StudyMCQ>()
        var currentQ: String? = null
        val currentOpts = mutableListOf<String>()
        var currentExp = "Explanation provided in lesson notes."
        var currentCorrectIdx = 0

        fun flush() {
            if (currentQ != null && currentOpts.size >= 2) {
                while (currentOpts.size < 4) {
                    currentOpts.add("Option ${currentOpts.size + 1}")
                }
                list.add(
                    StudyMCQ(
                        id = UUID.randomUUID().toString(),
                        question = currentQ!!,
                        options = currentOpts.toList(),
                        correctIndex = currentCorrectIdx.coerceIn(0, currentOpts.size - 1),
                        correctAnswer = currentOpts.getOrElse(currentCorrectIdx) { currentOpts[0] },
                        explanation = currentExp,
                        difficulty = fallbackDifficulty,
                        topic = fallbackTopic
                    )
                )
            }
            currentQ = null
            currentOpts.clear()
            currentExp = "Explanation provided in lesson notes."
            currentCorrectIdx = 0
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Q") || trimmed.startsWith("1.") || trimmed.startsWith("2.") || trimmed.startsWith("3.") || trimmed.startsWith("4.") || trimmed.startsWith("5.")) {
                flush()
                currentQ = trimmed.substringAfter(".").substringAfter(":").trim().ifBlank { trimmed }
            } else if (trimmed.startsWith("A)") || trimmed.startsWith("A.") || trimmed.startsWith("(A)")) {
                currentOpts.add(trimmed.substring(2).trim().removePrefix(".").removePrefix(")"))
            } else if (trimmed.startsWith("B)") || trimmed.startsWith("B.") || trimmed.startsWith("(B)")) {
                currentOpts.add(trimmed.substring(2).trim().removePrefix(".").removePrefix(")"))
            } else if (trimmed.startsWith("C)") || trimmed.startsWith("C.") || trimmed.startsWith("(C)")) {
                currentOpts.add(trimmed.substring(2).trim().removePrefix(".").removePrefix(")"))
            } else if (trimmed.startsWith("D)") || trimmed.startsWith("D.") || trimmed.startsWith("(D)")) {
                currentOpts.add(trimmed.substring(2).trim().removePrefix(".").removePrefix(")"))
            } else if (trimmed.lowercase().contains("answer:") || trimmed.lowercase().contains("correct:")) {
                val ansStr = trimmed.substringAfter(":").trim().uppercase()
                currentCorrectIdx = when {
                    ansStr.startsWith("A") -> 0
                    ansStr.startsWith("B") -> 1
                    ansStr.startsWith("C") -> 2
                    ansStr.startsWith("D") -> 3
                    else -> 0
                }
            } else if (trimmed.lowercase().contains("explanation:")) {
                currentExp = trimmed.substringAfter(":").trim()
            }
        }
        flush()

        if (list.isEmpty()) {
            list.add(
                StudyMCQ(
                    id = UUID.randomUUID().toString(),
                    question = "Which concept best defines the given topic in $fallbackTopic?",
                    options = listOf("Standard definition", "Alternative theorem", "Secondary property", "Empirical rule"),
                    correctIndex = 0,
                    correctAnswer = "Standard definition",
                    explanation = raw.take(250),
                    difficulty = fallbackDifficulty,
                    topic = fallbackTopic
                )
            )
        }
        return list
    }

    fun buildPracticeQuestionsPrompt(
        subject: String,
        chapter: String,
        topic: String,
        difficulty: String,
        language: String
    ): String {
        return buildString {
            appendLine("Generate diverse practice questions for CBSE Class 10.")
            appendLine("Subject: $subject")
            appendLine("Chapter: $chapter")
            appendLine("Topic: $topic")
            appendLine("Difficulty: $difficulty")
            appendLine("Language: $language")
            appendLine()
            appendLine("Generate 1 question for EACH of the following specified types, clearly labeled:")
            appendLine("1. Short Answer Question (2-3 marks)")
            appendLine("2. Long Answer Question (4-5 marks)")
            appendLine("3. Numerical Question (with step-by-step solution guidance)")
            appendLine("4. Assertion-Reason Question (Assertion [A] & Reason [R])")
            appendLine("5. Case-based / Source-based Question")
            appendLine("6. Competency-based Question")
            appendLine("7. Application-based Question (Real-world scenario)")
            appendLine()
            appendLine("For each question provide:")
            appendLine("- Type: [Question Type]")
            appendLine("- Question: [Text]")
            appendLine("- Solution Guide / Marking Scheme: [Brief step-by-step guidance]")
        }
    }

    fun buildExplainAnswerPrompt(
        question: String,
        studentAnswer: String,
        subject: String,
        chapter: String,
        topic: String,
        language: String
    ): String {
        return buildString {
            appendLine("Act as a supportive, constructive CBSE Class 10 teacher.")
            appendLine("Review this student's answer.")
            appendLine("Subject: $subject | Chapter: $chapter | Topic: $topic")
            appendLine("Language: $language")
            if (question.isNotBlank()) {
                appendLine("Question: $question")
            }
            appendLine("Student's Answer:")
            appendLine(studentAnswer.trim())
            appendLine()
            appendLine("Provide constructive feedback under these 5 clear headings:")
            appendLine("1. What is Correct: (Affirm positive points and correct formulas/statements)")
            appendLine("2. What Needs Improvement: (Point out errors gently)")
            appendLine("3. Missing Concepts: (State required points or terms omitted)")
            appendLine("4. Step-by-Step Correction: (The complete correct method)")
            appendLine("5. A Better Way to Approach: (Exam strategy, presentation tip or shortcut)")
        }
    }

    fun parseAnswerEvaluation(raw: String): AnswerEvaluation {
        val lines = raw.lines()
        var currentSection = ""
        val sections = mutableMapOf<String, StringBuilder>()

        for (line in lines) {
            val trimmed = line.trim()
            val lower = trimmed.lowercase()
            when {
                lower.contains("what is correct") || lower.startsWith("1.") -> {
                    currentSection = "correct"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("correct") { StringBuilder() }.appendLine(content.trim())
                }
                lower.contains("needs improvement") || lower.startsWith("2.") -> {
                    currentSection = "improve"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("improve") { StringBuilder() }.appendLine(content.trim())
                }
                lower.contains("missing concepts") || lower.startsWith("3.") -> {
                    currentSection = "missing"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("missing") { StringBuilder() }.appendLine(content.trim())
                }
                lower.contains("step-by-step correction") || lower.contains("step by step") || lower.startsWith("4.") -> {
                    currentSection = "correction"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("correction") { StringBuilder() }.appendLine(content.trim())
                }
                lower.contains("better way") || lower.contains("better approach") || lower.startsWith("5.") -> {
                    currentSection = "better"
                    val content = trimmed.substringAfter(":", "").ifBlank { trimmed.substringAfter("-", "") }
                    if (content.isNotBlank()) sections.getOrPut("better") { StringBuilder() }.appendLine(content.trim())
                }
                currentSection.isNotEmpty() -> {
                    sections.getOrPut(currentSection) { StringBuilder() }.appendLine(line)
                }
            }
        }

        return AnswerEvaluation(
            whatIsCorrect = sections["correct"]?.toString()?.trim() ?: "Good attempt showing understanding of the core question.",
            whatNeedsImprovement = sections["improve"]?.toString()?.trim() ?: "Refine the presentation and check key calculation steps.",
            missingConcepts = sections["missing"]?.toString()?.trim() ?: "Ensure all official definition keywords are included.",
            stepByStepCorrection = sections["correction"]?.toString()?.trim() ?: raw,
            betterApproach = sections["better"]?.toString()?.trim() ?: "Write formulas clearly, state given values first, and highlight the final unit.",
            rawText = raw
        )
    }

    fun buildStudyPlanPrompt(
        classLevel: String,
        subjects: List<String>,
        examDate: String,
        availableTimePerDay: String,
        strongSubjects: String,
        weakTopics: String,
        preferredDays: String,
        language: String
    ): String {
        return buildString {
            appendLine("Generate a personalized, practical study revision plan.")
            appendLine("Class: $classLevel (CBSE)")
            appendLine("Subjects: ${subjects.joinToString(", ")}")
            appendLine("Exam Date / Target: $examDate")
            appendLine("Available Daily Study Time: $availableTimePerDay")
            appendLine("Strong Subjects: $strongSubjects")
            appendLine("Topics Needing More Practice: $weakTopics")
            appendLine("Preferred Study Days: $preferredDays")
            appendLine("Language: $language")
            appendLine()
            appendLine("Create a realistic day-by-day study schedule with high retention.")
            appendLine("For each daily task specify:")
            appendLine("- Day/Slot: (e.g. Day 1, Monday)")
            appendLine("- Subject:")
            appendLine("- Topic:")
            appendLine("- Practice Activity: (e.g. Solve 10 sample problems, diagram practice)")
            appendLine("- Revision Activity: (e.g. Flashcard formula recap, flash recall)")
            appendLine("- Suggested Duration: (e.g. 45 mins)")
            appendLine()
            appendLine("Note: Provide practical pedagogical guidance without claiming any guaranteed exam marks.")
        }
    }

    fun buildSummarizeMaterialPrompt(
        material: String,
        subject: String,
        chapter: String,
        topic: String,
        language: String
    ): String {
        return buildString {
            appendLine("Summarize the following user-provided study notes and material.")
            appendLine("Subject: $subject | Chapter: $chapter | Topic: $topic")
            appendLine("Language: $language")
            appendLine()
            appendLine("Student Notes:")
            appendLine(material.trim())
            appendLine()
            appendLine("Please provide:")
            appendLine("1. Executive Summary: (3-4 crisp sentences)")
            appendLine("2. Core Definitions & Key Formulas:")
            appendLine("3. High-Yield Points for Exam Revision:")
            appendLine("4. Quick 3-Question Self-Check Checklist:")
        }
    }
}
