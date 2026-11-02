package com.aistudy.aistudy.home

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase

class HomeFragment : Fragment() {

    private lateinit var tvUserName: TextView
    private lateinit var tvXp: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView
    private lateinit var tvCountLanguage: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvUserName = view.findViewById(R.id.tv_user_name)
        tvXp = view.findViewById(R.id.tv_xp)
        tvStreak = view.findViewById(R.id.tv_streak)

        tvCountMath = view.findViewById(R.id.tv_count_math)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        tvCountHistory = view.findViewById(R.id.tv_count_history)
        tvCountLanguage = view.findViewById(R.id.tv_count_language)

        view.findViewById<View>(R.id.btn_quick_ask)?.setOnClickListener {
            (activity as? MainActivity)?.let { main ->
                val bottomNav = main.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
                bottomNav?.selectedItemId = R.id.nav_ask
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
        val context = context ?: return
        val userId = requireActivity().getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) return

        val db = AppDatabase.getInstance(context)
        val userDao = db.userDao()
        val profileDao = db.profileDao()
        val qaDao = db.questionAnswerDao()

        val user = userDao.findByEmail(
            requireActivity().getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
                .getString("email", "") ?: "",
        )
        val profile = profileDao.getByUserId(userId)

        val name = user?.fullName ?: "Student"
        tvUserName.text = name
        tvXp.text = getString(R.string.home_xp_label, profile?.xp ?: 0)
        tvStreak.text = getString(R.string.home_streak_label, profile?.streak ?: 0)

        tvCountMath.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "math"))
        tvCountScience.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "science"))
        tvCountCode.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "code"))
        tvCountHistory.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "history"))
        tvCountLanguage.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "language"))
    }
}
