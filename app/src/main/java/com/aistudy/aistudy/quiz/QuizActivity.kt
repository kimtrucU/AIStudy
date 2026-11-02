package com.aistudy.aistudy.quiz

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuizItem
import com.aistudy.aistudy.database.QuizSession
import com.aistudy.aistudy.database.QuizSessionDao
import com.aistudy.aistudy.xp.XpRules

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SUBJECT = "extra_subject"
    }

    private lateinit var layoutQuiz: LinearLayout
    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutResult: LinearLayout
    private lateinit var tvResultScore: TextView
    private lateinit var tvResultXp: TextView

    private lateinit var sessionDao: QuizSessionDao
    private lateinit var profileDao: ProfileDao
    private lateinit var session: QuizSession
    private val items = mutableListOf<QuizItem>()
    private var userId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        layoutQuiz = findViewById(R.id.layout_quiz)
        layoutLoading = findViewById(R.id.layout_loading)
        layoutResult = findViewById(R.id.layout_result)
        tvResultScore = findViewById(R.id.tv_result_score)
        tvResultXp = findViewById(R.id.tv_result_xp)

        val db = AppDatabase.getInstance(this)
        sessionDao = db.quizSessionDao()
        profileDao = db.profileDao()
    }

    private fun finishQuiz() {
        val score = items.count { it.correct == true }
        val xpGain = score * XpRules.QUIZ_CORRECT + XpRules.QUIZ_COMPLETE_BONUS
        session.score = score
        session.total = items.size
        session.completed = true
        sessionDao.update(session)

        val profile = profileDao.getByUserId(userId)
        if (profile != null) {
            XpRules.recordDailyActivity(profile, profileDao)
            XpRules.addXp(profile, profileDao, xpGain)
        }

        layoutQuiz.visibility = View.GONE
        layoutLoading.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE
        tvResultScore.text = getString(R.string.quiz_result_score, score, items.size)
        tvResultXp.text = getString(R.string.quiz_result_xp, xpGain)
    }
}
