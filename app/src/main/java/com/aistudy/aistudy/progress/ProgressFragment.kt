package com.aistudy.aistudy.progress

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase

class ProgressFragment : Fragment() {

    private lateinit var tvXp: TextView
    private lateinit var tvStreak: TextView
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

    private lateinit var rowLanguage: LinearLayout
    private lateinit var tvCountLanguage: TextView
    private lateinit var barLanguage: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvXp = view.findViewById(R.id.tv_xp)
        tvStreak = view.findViewById(R.id.tv_streak)
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

        rowLanguage = view.findViewById(R.id.row_language)
        tvCountLanguage = view.findViewById(R.id.tv_count_language)
        barLanguage = view.findViewById(R.id.bar_language)
    }

    override fun onResume() {
        super.onResume()
        loadProgress()
    }

    private fun loadProgress() {
        val context = context ?: return
        val userId = requireActivity().getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) return

        val db = AppDatabase.getInstance(context)
        val profileDao = db.profileDao()
        val qaDao = db.questionAnswerDao()

        val profile = profileDao.getByUserId(userId)
        tvXp.text = (profile?.xp ?: 0).toString()
        tvStreak.text = (profile?.streak ?: 0).toString()

        val countMath = qaDao.countBySubject(userId, "math")
        val countScience = qaDao.countBySubject(userId, "science")
        val countCode = qaDao.countBySubject(userId, "code")
        val countHistory = qaDao.countBySubject(userId, "history")
        val countLanguage = qaDao.countBySubject(userId, "language")
        val subjectTotal = countMath + countScience + countCode + countHistory + countLanguage
        val barDenom = if (subjectTotal > 0) subjectTotal else 1

        bindSubjectRow(rowMath, tvCountMath, barMath, countMath, barDenom)
        bindSubjectRow(rowScience, tvCountScience, barScience, countScience, barDenom)
        bindSubjectRow(rowCode, tvCountCode, barCode, countCode, barDenom)
        bindSubjectRow(rowHistory, tvCountHistory, barHistory, countHistory, barDenom)
        bindSubjectRow(rowLanguage, tvCountLanguage, barLanguage, countLanguage, barDenom)

        tvSubjectsEmpty.visibility = if (subjectTotal == 0) View.VISIBLE else View.GONE
    }

    private fun bindSubjectRow(
        row: LinearLayout,
        tvCount: TextView,
        bar: ProgressBar,
        count: Int,
        total: Int,
    ) {
        if (count > 0) {
            row.visibility = View.VISIBLE
            tvCount.text = count.toString()
            bar.progress = (count * 100) / total
        } else {
            row.visibility = View.GONE
        }
    }
}
