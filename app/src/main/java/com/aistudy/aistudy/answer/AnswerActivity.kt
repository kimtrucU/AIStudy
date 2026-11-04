package com.aistudy.aistudy.answer

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.ask.GeminiClient
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.quiz.QuizActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AnswerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QA_ID = "qa_id"

        private const val XP_REVIEW = 10
        private const val KEY_SIMPLIFY = "simplify"
        private const val KEY_ALTERNATIVE = "alternative"
        private const val KEY_CONCEPTS = "concepts"
        private const val KEY_MISTAKES = "mistakes"
        private const val KEY_FOLLOWUPS = "followUps"

        private fun isSameDay(a: Long, b: Long): Boolean {
            val ca = Calendar.getInstance()
            ca.timeInMillis = a
            val cb = Calendar.getInstance()
            cb.timeInMillis = b
            return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
        }
    }

    private var qaDao: QuestionAnswerDao? = null
    private lateinit var profileDao: ProfileDao
    private var qa: QuestionAnswer? = null
    private var executor: ExecutorService? = null
    private var openTimeMs: Long = 0

    private lateinit var tvDifficulty: TextView
    private lateinit var btnSimplify: TextView
    private lateinit var btnAlternative: TextView
    private lateinit var btnConcepts: TextView
    private lateinit var btnMistakes: TextView
    private lateinit var btnFollowups: TextView
    private lateinit var tvExtras: TextView
    private lateinit var progressExtras: ProgressBar
    private lateinit var btnMarkReviewed: MaterialButton

    private var activeExtraKey: String? = null
    private var extrasLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_answer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        openTimeMs = System.currentTimeMillis()

        val qaId = intent.getLongExtra(EXTRA_QA_ID, -1L)
        if (qaId == -1L) {
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val db = AppDatabase.getInstance(this)
        val dao = db.questionAnswerDao()
        qaDao = dao
        profileDao = db.profileDao()
        val item = dao.getById(qaId)
        qa = item
        if (item == null) {
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        executor = Executors.newSingleThreadExecutor()

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val tvSubject = findViewById<TextView>(R.id.tv_subject)
        val tvQuestion = findViewById<TextView>(R.id.tv_question)
        val layoutSteps = findViewById<LinearLayout>(R.id.layout_steps)
        val tvFinalAnswer = findViewById<TextView>(R.id.tv_final_answer)
        tvDifficulty = findViewById(R.id.tv_difficulty)
        btnSimplify = findViewById(R.id.btn_simplify)
        btnAlternative = findViewById(R.id.btn_alternative)
        btnConcepts = findViewById(R.id.btn_concepts)
        btnMistakes = findViewById(R.id.btn_mistakes)
        btnFollowups = findViewById(R.id.btn_followups)
        tvExtras = findViewById(R.id.tv_extras)
        progressExtras = findViewById(R.id.progress_extras)
        btnMarkReviewed = findViewById(R.id.btn_mark_reviewed)
        val btnPractice = findViewById<MaterialButton>(R.id.btn_practice)

        btnBack.setOnClickListener { finish() }
        tvSubject.text = subjectLabel(item.subject)
        tvQuestion.text = item.question
        tvFinalAnswer.text = item.finalAnswer
        tvDifficulty.text = difficultyLabel(item.difficulty)

        btnPractice.text = getString(R.string.quiz_practice_on, subjectLabel(item.subject))
        btnPractice.setOnClickListener {
            val practiceIntent = Intent(this, QuizActivity::class.java)
            practiceIntent.putExtra(QuizActivity.EXTRA_SUBJECT, item.subject)
            startActivity(practiceIntent)
        }

        val imagePath = item.imagePath
        val layoutImage = findViewById<LinearLayout>(R.id.layout_answer_image)
        val imgAnswer = findViewById<ImageView>(R.id.img_answer)
        if (!imagePath.isNullOrEmpty() && File(imagePath).exists()) {
            val bmp = BitmapFactory.decodeFile(imagePath)
            if (bmp != null) {
                imgAnswer.setImageBitmap(bmp)
                layoutImage.visibility = View.VISIBLE
            } else {
                layoutImage.visibility = View.GONE
            }
        } else {
            layoutImage.visibility = View.GONE
        }

        val stepsStr = item.steps
        if (!stepsStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(stepsStr)
                for (i in 0 until array.length()) {
                    val stepObj = array.getJSONObject(i)
                    addStepView(layoutSteps, i + 1, stepObj)
                }
            } catch (e: Exception) {
                val fallback = TextView(this)
                fallback.text = stepsStr
                fallback.setTextColor(getColor(R.color.auth_foreground))
                layoutSteps.addView(fallback)
            }
        }

        updateReviewedButtonState()
        btnMarkReviewed.setOnClickListener {
            onMarkReviewedClicked()
        }

        btnSimplify.setOnClickListener { onExtraClicked(KEY_SIMPLIFY) }
        btnAlternative.setOnClickListener { onExtraClicked(KEY_ALTERNATIVE) }
        btnConcepts.setOnClickListener { onExtraClicked(KEY_CONCEPTS) }
        btnMistakes.setOnClickListener { onExtraClicked(KEY_MISTAKES) }
        btnFollowups.setOnClickListener { onExtraClicked(KEY_FOLLOWUPS) }

        styleExtraButtons()
    }

    private fun updateReviewedButtonState() {
        val item = qa ?: return
        if (item.reviewedAt > 0) {
            btnMarkReviewed.isEnabled = false
            btnMarkReviewed.setText(R.string.answer_reviewed)
            btnMarkReviewed.setBackgroundColor(getColor(R.color.auth_card))
            btnMarkReviewed.setTextColor(getColor(R.color.auth_muted_foreground))
        } else {
            btnMarkReviewed.isEnabled = true
            btnMarkReviewed.setText(R.string.answer_mark_reviewed)
        }
    }

    private fun onMarkReviewedClicked() {
        val item = qa ?: return
        if (item.reviewedAt > 0) return

        val now = System.currentTimeMillis()
        item.reviewedAt = now
        qaDao?.updateReviewedAt(item.id, now)

        val profile = profileDao.getByUserId(item.userId)
        if (profile != null) {
            profile.xp += XP_REVIEW
            profileDao.update(profile)
        }

        Toast.makeText(this, R.string.answer_review_xp_toast, Toast.LENGTH_SHORT).show()
        updateReviewedButtonState()
    }

    private fun onExtraClicked(key: String) {
        if (extrasLoading) return
        activeExtraKey = key
        styleExtraButtons()

        val item = qa ?: return
        val cached = getCachedExtra(item.extrasJson, key)
        if (!TextUtils.isEmpty(cached)) {
            tvExtras.text = cached
            return
        }

        requestExtraFromAI(item, key)
    }

    private fun getCachedExtra(jsonStr: String?, key: String): String? {
        if (jsonStr.isNullOrEmpty()) return null
        return try {
            val json = JSONObject(jsonStr)
            if (json.has(key)) json.getString(key) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun putCachedExtra(jsonStr: String?, key: String, value: String): String {
        val json = if (!jsonStr.isNullOrEmpty()) {
            try {
                JSONObject(jsonStr)
            } catch (_: Exception) {
                JSONObject()
            }
        } else {
            JSONObject()
        }
        json.put(key, value)
        return json.toString()
    }

    private fun requestExtraFromAI(item: QuestionAnswer, key: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "YOUR_REAL_GEMINI_API_KEY" || apiKey == "YOUR_KEY_HERE") {
            Toast.makeText(this, R.string.ask_no_api_key, Toast.LENGTH_LONG).show()
            return
        }

        extrasLoading = true
        progressExtras.visibility = View.VISIBLE
        tvExtras.setText(R.string.answer_extras_loading)

        val prompt = buildExtraPrompt(item, key)

        executor?.execute {
            try {
                val rawResponse = GeminiClient.ask(apiKey, prompt)
                val clean = extractJson(rawResponse)
                val json = JSONObject(clean)
                val resultText = json.getString("result")

                val updatedJson = putCachedExtra(item.extrasJson, key, resultText)
                item.extrasJson = updatedJson
                qaDao?.updateExtras(item.id, updatedJson)

                runOnUiThread {
                    extrasLoading = false
                    progressExtras.visibility = View.GONE
                    if (activeExtraKey == key) {
                        tvExtras.text = resultText
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    extrasLoading = false
                    progressExtras.visibility = View.GONE
                    tvExtras.setText(R.string.answer_extras_error)
                }
            }
        }
    }

    private fun buildExtraPrompt(item: QuestionAnswer, key: String): String {
        val instruction = when (key) {
            KEY_SIMPLIFY -> "Explain the solution to this question in simpler, everyday language suitable for a beginner."
            KEY_ALTERNATIVE -> "Provide an alternative method or perspective to solve this exact question."
            KEY_CONCEPTS -> "List the key concepts, rules, and formulas used in this solution with brief explanations."
            KEY_MISTAKES -> "List the 2-3 most common mistakes students make when trying to solve this kind of question."
            KEY_FOLLOWUPS -> "Provide 3 practice or follow-up questions that reinforce the same skill, with brief answers in parentheses."
            else -> "Explain the solution in more detail."
        }
        return "You are an AI study tutor helping a student understand their study problem.\n" +
            "Task: $instruction\n\n" +
            "Always respond in English.\n" +
            "Return ONLY valid JSON (no markdown) with this structure:\n" +
            "{\"result\": \"your detailed response text here\"}\n\n" +
            "Question details:\n" +
            "Subject: ${item.subject}\n" +
            "Difficulty: ${item.difficulty ?: "medium"}\n" +
            "Question: ${item.question}\n" +
            "Final answer: ${item.finalAnswer}\n" +
            "Steps JSON: ${item.steps}"
    }

    private fun styleExtraButtons() {
        styleAction(btnSimplify, KEY_SIMPLIFY == activeExtraKey)
        styleAction(btnAlternative, KEY_ALTERNATIVE == activeExtraKey)
        styleAction(btnConcepts, KEY_CONCEPTS == activeExtraKey)
        styleAction(btnMistakes, KEY_MISTAKES == activeExtraKey)
        styleAction(btnFollowups, KEY_FOLLOWUPS == activeExtraKey)
    }

    private fun styleAction(btn: TextView, active: Boolean) {
        if (active) {
            btn.setBackgroundResource(R.drawable.bg_answer_action_active)
            btn.setTextColor(ContextCompat.getColor(this, R.color.auth_primary_foreground))
        } else {
            btn.setBackgroundResource(R.drawable.bg_answer_action)
            btn.setTextColor(ContextCompat.getColor(this, R.color.auth_foreground))
        }
    }

    private fun addStepView(parent: LinearLayout, number: Int, step: JSONObject) {
        val density = resources.displayMetrics.density
        val stepNumber = TextView(this)
        stepNumber.text = number.toString()
        stepNumber.gravity = Gravity.CENTER
        stepNumber.setTextColor(getColor(R.color.auth_primary_foreground))
        stepNumber.setBackgroundColor(getColor(R.color.auth_brand))
        val size = (24 * density).toInt()
        val numberParams = LinearLayout.LayoutParams(size, size)
        numberParams.setMargins(0, 0, (12 * density).toInt(), 0)
        stepNumber.layoutParams = numberParams

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val contentParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        contentParams.bottomMargin = (16 * density).toInt()
        content.layoutParams = contentParams

        val title = step.optString("title", "")
        if (title.isNotEmpty()) {
            val tvTitle = TextView(this)
            tvTitle.text = title
            tvTitle.setTextColor(getColor(R.color.auth_foreground))
            tvTitle.textSize = 14f
            tvTitle.setTypeface(null, Typeface.BOLD)
            content.addView(tvTitle)
        }

        val body = step.optString("body", "")
        val tvBody = TextView(this)
        tvBody.text = body
        tvBody.setTextColor(getColor(R.color.auth_foreground))
        tvBody.textSize = 14f
        content.addView(tvBody)

        val expr = step.optString("expr", "")
        if (expr.isNotEmpty()) {
            val tvExpr = TextView(this)
            tvExpr.text = expr
            tvExpr.setTextColor(getColor(R.color.auth_foreground))
            tvExpr.textSize = 14f
            tvExpr.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
            tvExpr.setPadding(12, 8, 12, 8)
            tvExpr.setBackgroundColor(getColor(R.color.auth_surface))
            val exprParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            exprParams.topMargin = (8 * density).toInt()
            tvExpr.layoutParams = exprParams
            content.addView(tvExpr)
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(stepNumber)
        row.addView(content)
        parent.addView(row)
    }

    private fun subjectLabel(subject: String?): String {
        return when (subject) {
            "math" -> getString(R.string.subject_math)
            "science" -> getString(R.string.subject_science)
            "code" -> getString(R.string.subject_code)
            "history" -> getString(R.string.subject_history)
            "language" -> getString(R.string.subject_language)
            else -> getString(R.string.subject_other)
        }
    }

    private fun difficultyLabel(difficulty: String?): String {
        return when (difficulty) {
            "easy" -> getString(R.string.answer_difficulty_easy)
            "hard" -> getString(R.string.answer_difficulty_hard)
            else -> getString(R.string.answer_difficulty_medium)
        }
    }

    private fun extractJson(raw: String): String {
        val text = raw.trim()
        if (text.startsWith("```")) {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1)
            }
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1)
        }
        return text
    }

    override fun onDestroy() {
        val elapsedSeconds = ((System.currentTimeMillis() - openTimeMs) / 1000).toInt()
        val item = qa
        if (elapsedSeconds > 0 && item != null) {
            qaDao?.addReviewedSeconds(item.id, elapsedSeconds)
        }
        executor?.shutdown()
        super.onDestroy()
    }
}
