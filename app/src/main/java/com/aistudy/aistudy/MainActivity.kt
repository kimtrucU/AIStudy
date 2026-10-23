package com.aistudy.aistudy

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import com.aistudy.aistudy.ask.AskFragment
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.history.HistoryFragment
import com.aistudy.aistudy.home.HomeFragment
import com.aistudy.aistudy.onboarding.OnboardingActivity
import com.aistudy.aistudy.progress.ProgressFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) {
            navigateToLogin()
            return
        }

        try {
            val userDao = AppDatabase.getInstance(this).userDao()
            val user = userDao.findById(userId)
            if (user == null) {
                prefs.edit { remove(LoginActivity.KEY_USER_ID) }
                navigateToLogin()
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
            prefs.edit { remove(LoginActivity.KEY_USER_ID) }
            navigateToLogin()
            return
        }

        ProfileDefaults.ensureExists(AppDatabase.getInstance(this).profileDao(), userId)
        val profile = AppDatabase.getInstance(this).profileDao().getByUserId(userId)
        if ((profile != null) && !profile.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    showLogoutConfirmationDialog()
                    true
                }
                else -> false
            }
        }

        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            val (targetFragment, isSameTab) = when (item.itemId) {
                R.id.nav_home -> Pair(HomeFragment(), currentFragment is HomeFragment)
                R.id.nav_ask -> Pair(AskFragment(), currentFragment is AskFragment)
                R.id.nav_history -> Pair(HistoryFragment(), currentFragment is HistoryFragment)
                R.id.nav_progress -> Pair(ProgressFragment(), currentFragment is ProgressFragment)
                else -> return@setOnItemSelectedListener false
            }

            if (isSameTab) {
                return@setOnItemSelectedListener true
            }

            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, targetFragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.auth_logout_confirm_title)
            .setMessage(R.string.auth_logout_confirm_message)
            .setPositiveButton(R.string.auth_logout) { _, _ ->
                logout()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun logout() {
        getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .edit()
            .remove(LoginActivity.KEY_USER_ID)
            .apply()

        navigateToLogin()
    }
}
