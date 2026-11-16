package com.aistudy.aistudy.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aistudy.aistudy.R

class CustomBottomNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val tabHome: View
    private val tabAsk: View
    private val tabHistory: View
    private val tabProgress: View

    private val pillHome: View
    private val pillAsk: View
    private val pillHistory: View
    private val pillProgress: View

    private val iconHome: ImageView
    private val iconAsk: ImageView
    private val iconHistory: ImageView
    private val iconProgress: ImageView

    private val textHome: TextView
    private val textAsk: TextView
    private val textHistory: TextView
    private val textProgress: TextView

    private var onItemSelectedListener: ((Int) -> Boolean)? = null

    var selectedItemId: Int = R.id.nav_home
        set(value) {
            field = value
            updateSelection(value)
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_custom_bottom_nav, this, true)
        setBackgroundResource(R.drawable.bg_bottom_nav_pill)

        tabHome = findViewById(R.id.tab_home)
        tabAsk = findViewById(R.id.tab_ask)
        tabHistory = findViewById(R.id.tab_history)
        tabProgress = findViewById(R.id.tab_progress)

        pillHome = findViewById(R.id.pill_home)
        pillAsk = findViewById(R.id.pill_ask)
        pillHistory = findViewById(R.id.pill_history)
        pillProgress = findViewById(R.id.pill_progress)

        iconHome = findViewById(R.id.icon_home)
        iconAsk = findViewById(R.id.icon_ask)
        iconHistory = findViewById(R.id.icon_history)
        iconProgress = findViewById(R.id.icon_progress)

        textHome = findViewById(R.id.text_home)
        textAsk = findViewById(R.id.text_ask)
        textHistory = findViewById(R.id.text_history)
        textProgress = findViewById(R.id.text_progress)

        tabHome.setOnClickListener { handleTabClick(R.id.nav_home) }
        tabAsk.setOnClickListener { handleTabClick(R.id.nav_ask) }
        tabHistory.setOnClickListener { handleTabClick(R.id.nav_history) }
        tabProgress.setOnClickListener { handleTabClick(R.id.nav_progress) }

        updateSelection(R.id.nav_home)
    }

    fun setOnItemSelectedListener(listener: (Int) -> Boolean) {
        this.onItemSelectedListener = listener
    }

    private fun handleTabClick(itemId: Int) {
        val consumed = onItemSelectedListener?.invoke(itemId) ?: true
        if (consumed) {
            selectedItemId = itemId
        }
    }

    private fun updateSelection(selectedId: Int) {
        setTabState(tabHome, pillHome, iconHome, textHome, selectedId == R.id.nav_home)
        setTabState(tabAsk, pillAsk, iconAsk, textAsk, selectedId == R.id.nav_ask)
        setTabState(tabHistory, pillHistory, iconHistory, textHistory, selectedId == R.id.nav_history)
        setTabState(tabProgress, pillProgress, iconProgress, textProgress, selectedId == R.id.nav_progress)
    }

    private fun setTabState(
        tab: View,
        pill: View,
        icon: ImageView,
        text: TextView,
        isSelected: Boolean
    ) {
        val colorActive = ContextCompat.getColor(context, R.color.auth_primary_foreground)
        val colorInactive = ContextCompat.getColor(context, R.color.auth_muted_foreground)

        if (isSelected) {
            pill.setBackgroundResource(R.drawable.bg_nav_item_active)
            icon.setColorFilter(colorActive)
            text.setTextColor(colorActive)
            text.alpha = 1.0f
        } else {
            pill.background = null
            icon.setColorFilter(colorInactive)
            text.setTextColor(colorInactive)
            text.alpha = 0.7f
        }
    }
}
