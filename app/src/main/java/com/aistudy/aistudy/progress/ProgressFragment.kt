package com.aistudy.aistudy.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuestionAnswerDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ProgressFragment : Fragment() {

    companion object {
        private const val XP_MAX = 1200
        private const val MIN_PRACTICE_QA = 2
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val WINDOW_MS = 30L * DAY_MS
        private val SUBJECTS = arrayOf(
            "math", "science", "code", "history", "language"
        )
    }

    private lateinit var tvUserEmail: TextView
    private lateinit var btnLogout: TextView
    private lateinit var tvXpTotal: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvStatStreak: TextView
    private lateinit var tvStatQuestions: TextView
    private lateinit var tvStatStars: TextView

    private lateinit var tvQuizAccuracy: TextView
    private lateinit var tvReviewTime: TextView
    private lateinit var tvInsight1: TextView
    private lateinit var tvInsight2: TextView
    private lateinit var tvInsightsEmpty: TextView
    private lateinit var cardReviewSuggest: LinearLayout
    private lateinit var tvReviewSubtitle: TextView

    private lateinit var rowMath: LinearLayout
    private lateinit var rowScience: LinearLayout
    private lateinit var rowCode: LinearLayout
    private lateinit var rowHistory: LinearLayout
    private lateinit var rowLanguage: LinearLayout
    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView
    private lateinit var tvCountLanguage: TextView
    private lateinit var barMath: ProgressBar
    private lateinit var barScience: ProgressBar
    private lateinit var barCode: ProgressBar
    private lateinit var barHistory: ProgressBar
    private lateinit var barLanguage: ProgressBar
    private lateinit var tvSubjectsEmpty: TextView

    private lateinit var badgeStarter: LinearLayout
    private lateinit var badgeWeek: LinearLayout
    private lateinit var badgeMath: LinearLayout
    private lateinit var badgeScholar: LinearLayout

    private var qaDao: QuestionAnswerDao? = null
    private var currentUserId = -1L
    private var reviewSubject: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }
}

    private fun loadProgress() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pDao = profileDao ?: return@launch
            val qDao = qaDao ?: return@launch

            val profile = pDao.getByUserId(userId) ?: return@launch
            val xp = profile.xp
            val streak = profile.streak
            val level = profile.level?.toIntOrNull() ?: 1

            val allItems = qDao.getAllByUserId(userId)
            val totalQuestions = allItems.size
            val starredCount = allItems.count { it.starred }

            val countMath = qDao.countBySubject(userId, "math")
            val countScience = qDao.countBySubject(userId, "science")
            val countCode = qDao.countBySubject(userId, "code")
            val countHistory = qDao.countBySubject(userId, "history")
            val subjectTotal = countMath + countScience + countCode + countHistory

            withContext(Dispatchers.Main) {
                tvXpTotal.text = xp.toString()
                tvLevel.text = getString(R.string.progress_level_scholar, level)

                val percent = minOf(100, ((xp * 100f) / XP_MAX).roundToInt())
                progressXp.progress = percent
                tvXpPercent.text = getString(R.string.progress_xp_percent, percent)

                tvStatStreak.text = getString(R.string.progress_streak_value, streak)
                tvStatQuestions.text = totalQuestions.toString()
                tvStatStars.text = starredCount.toString()

                if (subjectTotal == 0) {
                    tvSubjectsEmpty.visibility = View.VISIBLE
                    rowMath.visibility = View.GONE
                    rowScience.visibility = View.GONE
                    rowCode.visibility = View.GONE
                    rowHistory.visibility = View.GONE
                } else {
                    tvSubjectsEmpty.visibility = View.GONE
                    val barDenom = if (subjectTotal > 0) subjectTotal else 1
                    bindSubjectRow(rowMath, tvCountMath, barMath, countMath, barDenom)
                    bindSubjectRow(rowScience, tvCountScience, barScience, countScience, barDenom)
                    bindSubjectRow(rowCode, tvCountCode, barCode, countCode, barDenom)
                    bindSubjectRow(rowHistory, tvCountHistory, barHistory, countHistory, barDenom)
                }

                setBadge(badgeStarter, totalQuestions >= 1)
                setBadge(badgeWeek, streak >= 7)
                setBadge(badgeMath, countMath >= 1)
                setBadge(badgeScholar, xp >= 1000)
            }
        }
    }

    private fun bindSubjectRow(
        row: LinearLayout,
        tvCount: TextView,
        bar: ProgressBar,
        count: Int,
        denom: Int
    ) {
        if (count <= 0) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        tvCount.text = count.toString()
        bar.progress = ((count * 100f) / denom).roundToInt()
    }

    private fun setBadge(badge: LinearLayout, earned: Boolean) {
        badge.setBackgroundResource(if (earned) R.drawable.bg_badge_earned else R.drawable.bg_badge_locked)
        badge.alpha = if (earned) 1f else 0.5f
    }

    companion object {
        private const val XP_MAX = 1200
    }
}