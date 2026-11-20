package com.aistudy.aistudy.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.aistudy.aistudy.R

object LogoutDialogHelper {

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_logout, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<View>(R.id.btn_logout_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btn_logout_confirm).setOnClickListener {
            dialog.dismiss()
            activity.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .remove(LoginActivity.KEY_USER_ID)
                .apply()
            val intent = Intent(activity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            activity.startActivity(intent)
        }

        dialog.show()
    }
}
