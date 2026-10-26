package com.aistudy.aistudy.answer

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import org.json.JSONArray

class AnswerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QA_ID = "qa_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_answer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val qaId = intent.getLongExtra(EXTRA_QA_ID, -1L)
        if (qaId == -1L) {
            Toast.makeText(this, "Invalid Question ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val qaDao = AppDatabase.getInstance(this).questionAnswerDao()
        val item = qaDao.getById(qaId)
        if (item == null) {
            Toast.makeText(this, "Question not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<TextView>(R.id.tv_subject).text = item.subject ?: "Other"
        findViewById<TextView>(R.id.tv_question).text = item.question ?: ""
        findViewById<TextView>(R.id.tv_final_answer).text = item.finalAnswer ?: ""

        val layoutSteps = findViewById<LinearLayout>(R.id.layout_steps)
        val stepsStr = item.steps
        if (!stepsStr.isNullOrEmpty()) {
            try {
                val stepsArray = JSONArray(stepsStr)
                for (i in 0 until stepsArray.length()) {
                    val stepObj = stepsArray.getJSONObject(i)
                    val title = stepObj.optString("title", "Step ${i + 1}")
                    val body = stepObj.optString("body", "")
                    val expr = stepObj.optString("expr", "")

                    val stepView = TextView(this)
                    stepView.text = "$title\n$body\n$expr\n"
                    stepView.textSize = 16f
                    stepView.setPadding(0, 0, 0, 16)
                    layoutSteps.addView(stepView)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
