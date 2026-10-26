package com.aistudy.aistudy.ask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AskFragment : Fragment() {

    companion object {
        const val TAG = "AskFragment"
        const val XP_PER_QUESTION = 50

        fun maskKey(key: String): String {
            if (key.length <= 8) return key
            return key.substring(0, 4) + "..." + key.substring(key.length - 4)
        }

        fun truncate(text: String, len: Int = 100): String {
            if (text.length <= len) return text
            return text.substring(0, len) + "..."
        }
    }

    private lateinit var editQuestion: EditText
    private lateinit var btnSend: Button
    private lateinit var progressLoading: ProgressBar
    private var executor: ExecutorService? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ask, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        executor = Executors.newSingleThreadExecutor()

        editQuestion = view.findViewById(R.id.edit_question)
        btnSend = view.findViewById(R.id.btn_send)
        progressLoading = view.findViewById(R.id.progress_loading)

        val suggestion1: Button = view.findViewById(R.id.btn_suggestion_1)
        val suggestion2: Button = view.findViewById(R.id.btn_suggestion_2)
        val suggestion3: Button = view.findViewById(R.id.btn_suggestion_3)
        val suggestion4: Button = view.findViewById(R.id.btn_suggestion_4)

        suggestion1.setOnClickListener { editQuestion.setText(R.string.ask_suggestion_1) }
        suggestion2.setOnClickListener { editQuestion.setText(R.string.ask_suggestion_2) }
        suggestion3.setOnClickListener { editQuestion.setText(R.string.ask_suggestion_3) }
        suggestion4.setOnClickListener { editQuestion.setText(R.string.ask_suggestion_4) }

        btnSend.setOnClickListener {
            submitQuestion()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor?.shutdown()
    }

    private fun setLoading(loading: Boolean) {
        btnSend.isEnabled = !loading
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            btnSend.setText(R.string.ask_sending)
        } else {
            btnSend.setText(R.string.ask_send)
        }
    }

    private fun submitQuestion() {
        val question = editQuestion.text.toString().trim()
        if (question.isEmpty()) {
            Toast.makeText(requireContext(), "Question cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || (apiKey == "YOUR_REAL_GEMINI_API_KEY")) {
            Toast.makeText(requireContext(), "GEMINI_API_KEY not set", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "submit blocked: api key missing or placeholder")
            return
        }

        setLoading(true)
        Log.i(TAG, "submit start")

        val prefs = requireContext().getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1)

        val profileDao = AppDatabase.getInstance(requireContext()).profileDao()
        val profile = profileDao.getByUserId(userId)
        
        val level = profile?.level ?: "high"
        val style = profile?.style ?: "stepwise"

        val prompt = buildPrompt(question, level, style)

        executor?.execute {
            try {
                val rawResponse = GeminiClient.ask(apiKey, prompt)
                if (rawResponse.isEmpty()) {
                    requireActivity().runOnUiThread {
                        setLoading(false)
                        Toast.makeText(requireContext(), "Could not get AI response", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                val jsonText = extractJson(rawResponse)
                val jsonObject = JSONObject(jsonText)
                val subject = jsonObject.optString("subject", "other")
                val steps = jsonObject.optJSONArray("steps")?.toString() ?: "[]"
                val finalAnswer = jsonObject.optString("finalAnswer", "")

                Log.i(TAG, "parsed subject=$subject steps=${jsonObject.optJSONArray("steps")?.length() ?: 0}")

                val qa = QuestionAnswer()
                qa.userId = userId
                qa.subject = subject
                qa.question = question
                qa.steps = steps
                qa.finalAnswer = finalAnswer
                qa.createdAt = System.currentTimeMillis()

                val qaDao = AppDatabase.getInstance(requireContext()).questionAnswerDao()
                val qaId = qaDao.insert(qa)
                Log.i(TAG, "saved qaId=$qaId")

                if (profile != null) {
                    profile.xp += XP_PER_QUESTION
                    profileDao.update(profile)
                }

                requireActivity().runOnUiThread {
                    setLoading(false)
                    editQuestion.setText("")
                    val intent = Intent(requireContext(), AnswerActivity::class.java)
                    intent.putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
                    startActivity(intent)
                }

            } catch (e: Exception) {
                Log.e(TAG, "submit failed", e)
                requireActivity().runOnUiThread {
                    setLoading(false)
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun extractJson(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json")
            if (text.endsWith("```")) {
                text = text.removeSuffix("```")
            }
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```")
            if (text.endsWith("```")) {
                text = text.removeSuffix("```")
            }
        }
        return text.trim()
    }

    private fun buildPrompt(question: String, level: String, style: String): String {
        return """
            You are a helpful AI tutor. The user is a $level student.
            Provide the explanation in a $style style.
            Return ONLY a valid JSON object matching this schema, no markdown blocks, no extra text:
            {
              "subject": "math", // can be math, science, code, history, language, other
              "steps": [
                { "title": "Step 1", "body": "...", "expr": "x = 2" }
              ],
              "finalAnswer": "x = 2 or x = 3"
            }
            Question: $question
        """.trimIndent()
    }
}