package com.aistudy.aistudy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.ask.AskFragment
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.history.HistoryFragment
import com.aistudy.aistudy.home.HomeFragment
import com.aistudy.aistudy.notify.NotificationScheduler
import com.aistudy.aistudy.onboarding.OnboardingActivity
import com.aistudy.aistudy.progress.ProgressFragment
import com.aistudy.aistudy.xp.FriendSeeder
import com.aistudy.aistudy.xp.UnlockCatalog

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_QA_ID = "qa_id"
        const val TAB_HOME = "home"
        const val TAB_ASK = "ask"
        const val TAB_HISTORY = "history"
        const val TAB_PROGRESS = "progress"

        private val NAV_ITEMS = linkedMapOf(
            R.id.nav_home to (R.id.nav_home_icon to R.id.nav_home_label),
            R.id.nav_ask to (R.id.nav_ask_icon to R.id.nav_ask_label),
            R.id.nav_history to (R.id.nav_history_icon to R.id.nav_history_label),
            R.id.nav_progress to (R.id.nav_progress_icon to R.id.nav_progress_label),
        )
    }

    private var currentTabId = R.id.nav_home
    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val user = AppDatabase.getInstance(this).userDao().getById(userId)
        if (user == null) {
            prefs.edit().remove(LoginActivity.KEY_USER_ID).apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        ProfileDefaults.ensureExists(AppDatabase.getInstance(this).profileDao(), userId)
        val profile = AppDatabase.getInstance(this).profileDao().getByUserId(userId)
        if (profile != null && !profile.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        FriendSeeder.ensureSeeded(AppDatabase.getInstance(this).friendDao(), userId)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom)
            )
            insets
        }

        if (profile != null) {
            findViewById<View>(R.id.main).setBackgroundResource(
                UnlockCatalog.backgroundColorRes(UnlockCatalog.normalizeThemeId(profile.themeId))
            )
        }

        for (tabId in NAV_ITEMS.keys) {
            findViewById<View>(tabId).setOnClickListener { selectTab(tabId) }
        }

        if (savedInstanceState == null) {
            handleOpenIntent(intent)
        }

        requestNotificationPermissionIfNeeded()
        NotificationScheduler.schedule(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    fun selectTab(tabId: Int) {
        val fragment: Fragment = when (tabId) {
            R.id.nav_home -> HomeFragment()
            R.id.nav_ask -> AskFragment()
            R.id.nav_history -> HistoryFragment()
            R.id.nav_progress -> ProgressFragment()
            else -> return
        }
        currentTabId = tabId
        refreshNavState()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun refreshNavState() {
        val active = ContextCompat.getColor(this, R.color.nav_item_active)
        val inactive = ContextCompat.getColor(this, R.color.nav_item_inactive)
        for ((tabId, ids) in NAV_ITEMS) {
            val selected = tabId == currentTabId
            val color = if (selected) active else inactive
            val item = findViewById<View>(tabId)
            item.isSelected = selected
            if (selected) {
                item.setBackgroundResource(R.drawable.bg_side_nav_item_active)
            } else {
                item.background = null
            }
            findViewById<ImageView>(ids.first).setColorFilter(color)
            findViewById<TextView>(ids.second).setTextColor(color)
        }
    }

    private fun handleOpenIntent(intent: Intent?) {
        if (intent == null) {
            selectTab(R.id.nav_home)
            return
        }
        val qaId = intent.getLongExtra(EXTRA_QA_ID, -1)
        if (qaId > 0) {
            val answer = Intent(this, AnswerActivity::class.java)
            answer.putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
            startActivity(answer)
            selectTab(R.id.nav_history)
            return
        }
        selectTab(
            when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
                TAB_ASK -> R.id.nav_ask
                TAB_HISTORY -> R.id.nav_history
                TAB_PROGRESS -> R.id.nav_progress
                else -> R.id.nav_home
            }
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
