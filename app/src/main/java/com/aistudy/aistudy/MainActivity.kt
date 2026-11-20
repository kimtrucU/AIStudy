package com.aistudy.aistudy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
import com.aistudy.aistudy.view.CustomBottomNavView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_QA_ID = "qa_id"
        const val TAB_HOME = "home"
        const val TAB_ASK = "ask"
        const val TAB_HISTORY = "history"
        const val TAB_PROGRESS = "progress"
    }

    private lateinit var bottomNav: CustomBottomNavView
    private var activeTabId: Int = -1
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun selectTab(itemId: Int) {
        if (::bottomNav.isInitialized) {
            bottomNav.selectedItemId = itemId
            switchToTab(itemId)
        }
    }

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

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        bottomNav = findViewById(R.id.bottom_nav)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            bottomNav.visibility = if (imeVisible) View.GONE else View.VISIBLE
            val bottomInset = maxOf(systemBars.bottom, ime.bottom)
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            insets
        }

        if (profile != null) {
            findViewById<View>(R.id.main).setBackgroundResource(
                UnlockCatalog.backgroundColorRes(UnlockCatalog.normalizeThemeId(profile.themeId))
            )
        }

        bottomNav.setOnItemSelectedListener { itemId ->
            switchToTab(itemId)
            true
        }

        if (savedInstanceState == null) {
            handleOpenIntent(intent)
        } else {
            val current = supportFragmentManager.fragments.firstOrNull { !it.isHidden }
            activeTabId = when (current) {
                is AskFragment -> R.id.nav_ask
                is HistoryFragment -> R.id.nav_history
                is ProgressFragment -> R.id.nav_progress
                else -> R.id.nav_home
            }
            bottomNav.selectedItemId = activeTabId
        }

        requestNotificationPermissionIfNeeded()
        bgExecutor.execute {
            FriendSeeder.ensureSeeded(AppDatabase.getInstance(this).friendDao(), userId)
            NotificationScheduler.schedule(this)
        }
    }

    private fun switchToTab(itemId: Int) {
        if (activeTabId == itemId) return
        val tag = "tab_$itemId"
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        for (frag in fm.fragments) {
            if (frag.tag?.startsWith("tab_") == true && frag.tag != tag && !frag.isHidden) {
                tx.hide(frag)
            }
        }

        var target = fm.findFragmentByTag(tag)
        if (target == null) {
            target = when (itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_ask -> AskFragment()
                R.id.nav_history -> HistoryFragment()
                R.id.nav_progress -> ProgressFragment()
                else -> return
            }
            tx.add(R.id.fragment_container, target, tag)
        } else {
            tx.show(target)
        }
        tx.commit()
        activeTabId = itemId
    }

    override fun onDestroy() {
        bgExecutor.shutdown()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
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
        val targetTab = when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_ASK -> R.id.nav_ask
            TAB_HISTORY -> R.id.nav_history
            TAB_PROGRESS -> R.id.nav_progress
            else -> R.id.nav_home
        }
        selectTab(targetTab)
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
