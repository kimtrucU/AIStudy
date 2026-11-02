package com.aistudy.aistudy.progress

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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

    private lateinit var tvXpTotal: TextView
    private lateinit var tvLevel: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvXpPercent: TextView

    private lateinit var tvStatStreak: TextView
    private lateinit var tvStatQuestions: TextView
    private lateinit var tvStatStars: TextView

    private lateinit var tvSubjectsEmpty: TextView
    private lateinit var rowMath: LinearLayout
    private lateinit var tvCountMath: TextView
    private lateinit var barMath: ProgressBar

    private lateinit var rowScience: LinearLayout
    private lateinit var tvCountScience: TextView
    private lateinit var barScience: ProgressBar

    private lateinit var rowCode: LinearLayout
    private lateinit var tvCountCode: TextView
    private lateinit var barCode: ProgressBar

    private lateinit var rowHistory: LinearLayout
    private lateinit var tvCountHistory: TextView
    private lateinit var barHistory: ProgressBar

    private lateinit var badgeStarter: LinearLayout
    private lateinit var badgeWeek: LinearLayout
    private lateinit var badgeMath: LinearLayout
    private lateinit var badgeScholar: LinearLayout

    private var profileDao: ProfileDao? = null
    private var qaDao: QuestionAnswerDao? = null
    private var userId: Long = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvXpTotal = view.findViewById(R.id.tv_xp_total)
        tvLevel = view.findViewById(R.id.tv_level)
        progressXp = view.findViewById(R.id.progress_xp)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)

        tvStatStreak = view.findViewById(R.id.tv_stat_streak)
        tvStatQuestions = view.findViewById(R.id.tv_stat_questions)
        tvStatStars = view.findViewById(R.id.tv_stat_stars)

        tvSubjectsEmpty = view.findViewById(R.id.tv_subjects_empty)
        rowMath = view.findViewById(R.id.row_math)
        tvCountMath = view.findViewById(R.id.tv_count_math)
        barMath = view.findViewById(R.id.bar_math)

        rowScience = view.findViewById(R.id.row_science)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        barScience = view.findViewById(R.id.bar_science)

        rowCode = view.findViewById(R.id.row_code)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        barCode = view.findViewById(R.id.bar_code)

        rowHistory = view.findViewById(R.id.row_history)
        tvCountHistory = view.findViewById(R.id.tv_count_history)
        barHistory = view.findViewById(R.id.bar_history)

        badgeStarter = view.findViewById(R.id.badge_starter)
        badgeWeek = view.findViewById(R.id.badge_week)
        badgeMath = view.findViewById(R.id.badge_math)
        badgeScholar = view.findViewById(R.id.badge_scholar)

        val db = AppDatabase.getInstance(requireContext())
        profileDao = db.profileDao()
        qaDao = db.questionAnswerDao()

        val prefs = requireContext().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        userId = prefs.getLong("user_id", -1L)
    }

    override fun onResume() {
        super.onResume()
        if (userId != -1L) {
            loadProgress()
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