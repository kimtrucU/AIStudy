package com.aistudy.aistudy.progress

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.Friend
import com.aistudy.aistudy.database.FriendDao
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.database.QuizItemDao
import com.aistudy.aistudy.database.QuizSessionDao
import com.aistudy.aistudy.notify.NotificationScheduler
import com.aistudy.aistudy.quiz.QuizActivity
import com.aistudy.aistudy.xp.FriendSeeder
import com.aistudy.aistudy.xp.UnlockCatalog
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ProgressFragment : Fragment() {

    companion object {
        private const val MIN_PRACTICE_QA = 2
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val WINDOW_MS = 30L * DAY_MS
        private val SUBJECTS = arrayOf(
            "math", "science", "code", "history", "language"
        )
    }

    private data class LeaderboardEntry(
        val name: String,
        val weeklyXp: Int,
        val isYou: Boolean
    )

    private lateinit var tvAvatarBadge: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var btnLogout: TextView
    private lateinit var tvXpTotal: TextView
    private lateinit var tvLevel: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvXpPercent: TextView

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

    private lateinit var layoutAvatars: LinearLayout
    private lateinit var layoutThemes: LinearLayout
    private lateinit var layoutLeaderboard: LinearLayout

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

    private lateinit var badgeStarter: LinearLayout
    private lateinit var badgeStars: LinearLayout
    private lateinit var badgeStreak: LinearLayout
    private lateinit var badgePerfect: LinearLayout
    private lateinit var badgeMastery: LinearLayout
    private lateinit var badgeScholar: LinearLayout
    private lateinit var badgeWeek: LinearLayout
    private lateinit var badgeMath: LinearLayout
    private lateinit var btnCheckReminders: MaterialButton

    private var profileDao: ProfileDao? = null
    private var qaDao: QuestionAnswerDao? = null
    private var quizItemDao: QuizItemDao? = null
    private var quizSessionDao: QuizSessionDao? = null
    private var friendDao: FriendDao? = null
    private var userId: Long = -1L
    private var reviewSubject: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvAvatarBadge = view.findViewById(R.id.tv_avatar_badge)
        tvUserEmail = view.findViewById(R.id.tv_user_email)
        btnLogout = view.findViewById(R.id.btn_logout)
        tvXpTotal = view.findViewById(R.id.tv_xp_total)
        tvLevel = view.findViewById(R.id.tv_level)
        progressXp = view.findViewById(R.id.progress_xp)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)

        tvStatStreak = view.findViewById(R.id.tv_stat_streak)
        tvStatQuestions = view.findViewById(R.id.tv_stat_questions)
        tvStatStars = view.findViewById(R.id.tv_stat_stars)

        tvQuizAccuracy = view.findViewById(R.id.tv_quiz_accuracy)
        tvReviewTime = view.findViewById(R.id.tv_review_time)
        tvInsight1 = view.findViewById(R.id.tv_insight_1)
        tvInsight2 = view.findViewById(R.id.tv_insight_2)
        tvInsightsEmpty = view.findViewById(R.id.tv_insights_empty)
        cardReviewSuggest = view.findViewById(R.id.card_review_suggest)
        tvReviewSubtitle = view.findViewById(R.id.tv_review_subtitle)

        layoutAvatars = view.findViewById(R.id.layout_avatars)
        layoutThemes = view.findViewById(R.id.layout_themes)
        layoutLeaderboard = view.findViewById(R.id.layout_leaderboard)

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

        badgeStarter = view.findViewById(R.id.badge_starter)
        badgeStars = view.findViewById(R.id.badge_stars)
        badgeStreak = view.findViewById(R.id.badge_streak)
        badgePerfect = view.findViewById(R.id.badge_perfect)
        badgeMastery = view.findViewById(R.id.badge_mastery)
        badgeScholar = view.findViewById(R.id.badge_scholar)
        badgeWeek = view.findViewById(R.id.badge_week)
        badgeMath = view.findViewById(R.id.badge_math)
        btnCheckReminders = view.findViewById(R.id.btn_check_reminders)

        val db = AppDatabase.getInstance(requireContext())
        profileDao = db.profileDao()
        qaDao = db.questionAnswerDao()
        quizItemDao = db.quizItemDao()
        quizSessionDao = db.quizSessionDao()
        friendDao = db.friendDao()

        userId = requireContext()
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)

        btnLogout.setOnClickListener {
            val main = activity as? MainActivity
            if (main != null) {
                main.showLogoutConfirmationDialog()
            } else {
                logoutFallback()
            }
        }
        cardReviewSuggest.setOnClickListener {
            reviewSubject?.let { openPractice(it) }
        }
        btnCheckReminders.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            NotificationScheduler.enqueueNow(ctx)
            Toast.makeText(ctx, R.string.progress_force_notify, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (userId != -1L) {
            loadProgress()
        }
    }

    private fun logoutFallback() {
        val ctx = context ?: return
        ctx.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .remove(LoginActivity.KEY_USER_ID)
            .apply()
        val intent = Intent(ctx, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun loadProgress() {
        val ctx = context ?: return
        val db = AppDatabase.getInstance(ctx)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val pDao = profileDao ?: return@launch
            val qDao = qaDao ?: return@launch
            val qiDao = quizItemDao ?: return@launch
            val qsDao = quizSessionDao ?: return@launch
            val fDao = friendDao ?: return@launch

            val user = db.userDao().getById(userId)
            val profile = pDao.getByUserId(userId) ?: return@launch
            XpRules.ensureWeek(profile)
            FriendSeeder.ensureSeeded(fDao, userId)

            val xp = profile.xp
            val streak = profile.streak
            val level = XpRules.levelFromXp(xp)
            val percent = XpRules.progressPercentToNext(xp)

            val allItems = qDao.getAllByUserId(userId)
            val totalQuestions = allItems.size
            val starredCount = allItems.count { it.starred }

            val attempted = qiDao.countAttemptedByUserId(userId)
            val correct = qiDao.countCorrectByUserId(userId)
            val reviewSeconds = qDao.sumReviewedSecondsByUserId(userId)
            val perfectQuizzes = qsDao.countPerfectByUserId(userId)

            val topSubject = qDao.getTopSubjectByUserId(userId)
            val topSubjectCount = if (topSubject != null) qDao.countBySubject(userId, topSubject) else 0
            val improvedSubject = findImprovedSubject(qiDao, userId)

            val countMath = qDao.countBySubject(userId, "math")
            val countScience = qDao.countBySubject(userId, "science")
            val countCode = qDao.countBySubject(userId, "code")
            val countHistory = qDao.countBySubject(userId, "history")
            val countLanguage = qDao.countBySubject(userId, "language")
            val subjectTotal = countMath + countScience + countCode + countHistory + countLanguage
            val maxSubjectCount = maxOf(countMath, countScience, countCode, countHistory, countLanguage)

            val friends: List<Friend> = fDao.getByOwner(userId)

            withContext(Dispatchers.Main) {
                if (!isAdded || view == null) {
                    return@withContext
                }

                tvUserEmail.text = user?.email ?: ""
                val avatarId = UnlockCatalog.normalizeAvatarId(profile.avatarId)
                tvAvatarBadge.text = UnlockCatalog.emojiForAvatar(avatarId)

                tvXpTotal.text = xp.toString()
                tvLevel.text = getString(R.string.progress_level_scholar, level)
                progressXp.progress = percent
                tvXpPercent.text = getString(R.string.progress_xp_percent, percent)

                tvStatStreak.text = getString(R.string.progress_streak_value, streak)
                tvStatQuestions.text = totalQuestions.toString()
                tvStatStars.text = starredCount.toString()

                if (attempted > 0) {
                    val accuracyPct = ((correct * 100f) / attempted).roundToInt()
                    tvQuizAccuracy.text = getString(
                        R.string.progress_quiz_accuracy_value, accuracyPct, correct, attempted
                    )
                } else {
                    tvQuizAccuracy.setText(R.string.progress_quiz_accuracy_empty)
                }
                tvReviewTime.text = formatReviewTime(reviewSeconds)

                bindInsights(topSubject, topSubjectCount, improvedSubject)

                if (topSubject != null && topSubjectCount >= MIN_PRACTICE_QA) {
                    reviewSubject = topSubject
                    tvReviewSubtitle.text = getString(R.string.progress_review_subtitle, subjectLabel(topSubject))
                    cardReviewSuggest.visibility = View.VISIBLE
                } else {
                    reviewSubject = null
                    cardReviewSuggest.visibility = View.GONE
                }

                bindAvatars(profile, pDao, level)
                bindThemes(profile, pDao, level)
                bindLeaderboard(friends, profile.weeklyXp)

                if (subjectTotal == 0) {
                    tvSubjectsEmpty.visibility = View.VISIBLE
                    rowMath.visibility = View.GONE
                    rowScience.visibility = View.GONE
                    rowCode.visibility = View.GONE
                    rowHistory.visibility = View.GONE
                    rowLanguage.visibility = View.GONE
                } else {
                    tvSubjectsEmpty.visibility = View.GONE
                    val barDenom = maxOf(1, subjectTotal)
                    bindSubjectRow(rowMath, tvCountMath, barMath, countMath, barDenom)
                    bindSubjectRow(rowScience, tvCountScience, barScience, countScience, barDenom)
                    bindSubjectRow(rowCode, tvCountCode, barCode, countCode, barDenom)
                    bindSubjectRow(rowHistory, tvCountHistory, barHistory, countHistory, barDenom)
                    bindSubjectRow(rowLanguage, tvCountLanguage, barLanguage, countLanguage, barDenom)
                }

                setBadge(badgeStarter, totalQuestions >= 1)
                setBadge(badgeStars, starredCount >= 5)
                setBadge(badgeStreak, streak >= 3)
                setBadge(badgePerfect, perfectQuizzes >= 1)
                setBadge(badgeMastery, maxSubjectCount >= 5)
                setBadge(badgeScholar, xp >= 1000)
                setBadge(badgeWeek, streak >= 7)
                setBadge(badgeMath, countMath >= 1)
            }
        }
    }

    private fun bindAvatars(profile: Profile, pDao: ProfileDao, level: Int) {
        val ctx = context ?: return
        layoutAvatars.removeAllViews()
        val activeAvatar = UnlockCatalog.normalizeAvatarId(profile.avatarId)
        val density = resources.displayMetrics.density

        for ((index, avatarId) in UnlockCatalog.AVATARS.withIndex()) {
            val unlocked = UnlockCatalog.isAvatarUnlocked(avatarId, level)
            val selected = unlocked && avatarId == activeAvatar
            val reqLevel = UnlockCatalog.requiredLevelForAvatar(avatarId)

            val chip = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    (8 * density).roundToInt(),
                    (10 * density).roundToInt(),
                    (8 * density).roundToInt(),
                    (10 * density).roundToInt()
                )
                setBackgroundResource(
                    if (selected) R.drawable.bg_unlock_chip_selected else R.drawable.bg_unlock_chip
                )
                alpha = if (unlocked) 1f else 0.55f
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    if (index > 0) marginStart = (8 * density).roundToInt()
                }
            }

            val tvEmoji = TextView(ctx).apply {
                text = UnlockCatalog.emojiForAvatar(avatarId)
                textSize = 22f
                gravity = Gravity.CENTER
            }
            chip.addView(tvEmoji)

            if (!unlocked) {
                val tvLock = TextView(ctx).apply {
                    text = getString(R.string.progress_unlock_locked, reqLevel)
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(ctx, R.color.auth_muted_foreground))
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                chip.addView(tvLock)
            }

            chip.setOnClickListener {
                if (!unlocked) {
                    Toast.makeText(
                        ctx,
                        getString(R.string.progress_unlock_toast, reqLevel),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                profile.avatarId = avatarId
                pDao.update(profile)
                tvAvatarBadge.text = UnlockCatalog.emojiForAvatar(avatarId)
                bindAvatars(profile, pDao, level)
            }

            layoutAvatars.addView(chip)
        }
    }

    private fun bindThemes(profile: Profile, pDao: ProfileDao, level: Int) {
        val ctx = context ?: return
        layoutThemes.removeAllViews()
        val activeTheme = UnlockCatalog.normalizeThemeId(profile.themeId)
        val density = resources.displayMetrics.density

        for ((index, themeId) in UnlockCatalog.THEMES.withIndex()) {
            val unlocked = UnlockCatalog.isThemeUnlocked(themeId, level)
            val selected = unlocked && themeId == activeTheme
            val reqLevel = UnlockCatalog.requiredLevelForTheme(themeId)

            val chip = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    (10 * density).roundToInt(),
                    (12 * density).roundToInt(),
                    (10 * density).roundToInt(),
                    (12 * density).roundToInt()
                )
                setBackgroundResource(
                    if (selected) R.drawable.bg_unlock_chip_selected else R.drawable.bg_unlock_chip
                )
                alpha = if (unlocked) 1f else 0.55f
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    if (index > 0) marginStart = (8 * density).roundToInt()
                }
            }

            val tvName = TextView(ctx).apply {
                text = UnlockCatalog.labelForTheme(themeId)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(
                    ContextCompat.getColor(
                        ctx,
                        if (selected) R.color.auth_brand else R.color.auth_foreground
                    )
                )
                gravity = Gravity.CENTER
            }
            chip.addView(tvName)

            if (!unlocked) {
                val tvLock = TextView(ctx).apply {
                    text = getString(R.string.progress_unlock_locked, reqLevel)
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(ctx, R.color.auth_muted_foreground))
                    gravity = Gravity.CENTER
                }
                chip.addView(tvLock)
            }

            chip.setOnClickListener {
                if (!unlocked) {
                    Toast.makeText(
                        ctx,
                        getString(R.string.progress_unlock_toast, reqLevel),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                profile.themeId = themeId
                pDao.update(profile)
                Toast.makeText(ctx, R.string.progress_theme_applied, Toast.LENGTH_SHORT).show()
                bindThemes(profile, pDao, level)
                (activity as? MainActivity)?.applyTheme(themeId)
            }

            layoutThemes.addView(chip)
        }
    }

    private fun bindLeaderboard(friends: List<Friend>, myWeeklyXp: Int) {
        val ctx = context ?: return
        layoutLeaderboard.removeAllViews()

        val entries = mutableListOf<LeaderboardEntry>()
        for (f in friends) {
            entries.add(LeaderboardEntry(f.displayName ?: "", f.weeklyXp, isYou = false))
        }
        entries.add(LeaderboardEntry(getString(R.string.progress_leaderboard_you), myWeeklyXp, isYou = true))
        entries.sortWith(compareByDescending<LeaderboardEntry> { it.weeklyXp }.thenBy { !it.isYou })

        val inflater = LayoutInflater.from(ctx)
        for ((index, entry) in entries.withIndex()) {
            val row = inflater.inflate(R.layout.item_leaderboard_row, layoutLeaderboard, false)
            val tvRank = row.findViewById<TextView>(R.id.tv_rank)
            val tvName = row.findViewById<TextView>(R.id.tv_name)
            val tvWeeklyXp = row.findViewById<TextView>(R.id.tv_weekly_xp)

            tvRank.text = "#${index + 1}"
            tvName.text = entry.name
            tvWeeklyXp.text = getString(R.string.progress_leaderboard_xp, entry.weeklyXp)

            if (entry.isYou) {
                row.setBackgroundResource(R.drawable.bg_unlock_chip_selected)
                val brand = ContextCompat.getColor(ctx, R.color.auth_brand)
                tvRank.setTextColor(brand)
                tvName.setTextColor(brand)
            }
            layoutLeaderboard.addView(row)
        }
    }

    private fun bindInsights(
        topSubject: String?,
        topSubjectCount: Int,
        improvedSubject: String?
    ) {
        tvInsight1.visibility = View.GONE
        tvInsight2.visibility = View.GONE

        var shown = 0
        if (topSubject != null && topSubjectCount >= 1) {
            tvInsight1.text = getString(
                R.string.progress_insight_top_subject, subjectLabel(topSubject)
            )
            tvInsight1.visibility = View.VISIBLE
            shown++
        }

        if (improvedSubject != null) {
            val target = if (shown == 0) tvInsight1 else tvInsight2
            target.text = getString(
                R.string.progress_insight_accuracy_improved, subjectLabel(improvedSubject)
            )
            target.visibility = View.VISIBLE
            shown++
        }

        tvInsightsEmpty.visibility = if (shown == 0) View.VISIBLE else View.GONE
    }

    private fun findImprovedSubject(quizItemDao: QuizItemDao, userId: Long): String? {
        val now = System.currentTimeMillis()
        val recentFrom = now - WINDOW_MS
        val olderFrom = now - (2L * WINDOW_MS)

        var bestSubject: String? = null
        var bestDelta = 0f

        for (subject in SUBJECTS) {
            val recentAttempted = quizItemDao.countAttemptedByUserIdSubjectAndRange(
                userId, subject, recentFrom, now
            )
            val olderAttempted = quizItemDao.countAttemptedByUserIdSubjectAndRange(
                userId, subject, olderFrom, recentFrom
            )
            if (recentAttempted < 2 || olderAttempted < 2) {
                continue
            }
            val recentCorrect = quizItemDao.countCorrectByUserIdSubjectAndRange(
                userId, subject, recentFrom, now
            )
            val olderCorrect = quizItemDao.countCorrectByUserIdSubjectAndRange(
                userId, subject, olderFrom, recentFrom
            )
            val recentAcc = recentCorrect * 1f / recentAttempted
            val olderAcc = olderCorrect * 1f / olderAttempted
            val delta = recentAcc - olderAcc
            if (delta > 0.05f && delta > bestDelta) {
                bestDelta = delta
                bestSubject = subject
            }
        }
        return bestSubject
    }

    private fun openPractice(subject: String) {
        val ctx = context ?: return
        val dao = qaDao ?: return
        if (userId == -1L) {
            return
        }
        val count = dao.countBySubject(userId, subject)
        if (count < MIN_PRACTICE_QA) {
            Toast.makeText(ctx, R.string.quiz_need_more, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(ctx, QuizActivity::class.java)
        intent.putExtra(QuizActivity.EXTRA_SUBJECT, subject)
        startActivity(intent)
    }

    private fun formatReviewTime(totalSeconds: Int): String {
        if (totalSeconds < 60) {
            return getString(R.string.progress_review_time_seconds, totalSeconds)
        }
        if (totalSeconds < 3600) {
            return getString(R.string.progress_review_time_minutes, totalSeconds / 60)
        }
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return getString(R.string.progress_review_time_hours, hours, minutes)
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
}
