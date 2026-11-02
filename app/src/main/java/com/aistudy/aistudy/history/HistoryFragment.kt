package com.aistudy.aistudy.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryFragment : Fragment() {

    private lateinit var etSearch: EditText
    private lateinit var chipAll: TextView
    private lateinit var chipMath: TextView
    private lateinit var chipScience: TextView
    private lateinit var chipCode: TextView
    private lateinit var chipHistory: TextView
    private lateinit var chipStarred: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: HistoryAdapter
    private var qaDao: QuestionAnswerDao? = null
    private val allItems = mutableListOf<QuestionAnswer>()

    private var subjectFilter = "all"
    private var starredOnly = false
    private var query = ""
    
    private var userId: Long = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        etSearch = view.findViewById(R.id.et_search)
        chipAll = view.findViewById(R.id.chip_all)
        chipMath = view.findViewById(R.id.chip_math)
        chipScience = view.findViewById(R.id.chip_science)
        chipCode = view.findViewById(R.id.chip_code)
        chipHistory = view.findViewById(R.id.chip_history)
        chipStarred = view.findViewById(R.id.chip_starred)
        rvHistory = view.findViewById(R.id.rv_history)
        tvEmpty = view.findViewById(R.id.tv_empty)

        qaDao = AppDatabase.getInstance(requireContext()).questionAnswerDao()
        
        val prefs = requireContext().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        userId = prefs.getLong("user_id", -1L)

        adapter = HistoryAdapter(
            object : HistoryAdapter.Listener {
                override fun onItemClick(qa: QuestionAnswer) {
                    val intent = Intent(requireContext(), AnswerActivity::class.java)
                    intent.putExtra(AnswerActivity.EXTRA_QA_ID, qa.id)
                    startActivity(intent)
                }

                override fun onStarClick(qa: QuestionAnswer) {
                    val dao = qaDao ?: return
                    lifecycleScope.launch(Dispatchers.IO) {
                        dao.toggleStar(qa.id)
                        loadHistory()
                    }
                }
            },
            ::subjectLabel,
            ::relativeTime
        )
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter
        
        setupChips()
        
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                applyFilter()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (userId != -1L) {
            lifecycleScope.launch(Dispatchers.IO) {
                loadHistory()
            }
        }
    }

    private suspend fun loadHistory() {
        val dao = qaDao ?: return
        val list = dao.getAllByUserId(userId)
        withContext(Dispatchers.Main) {
            allItems.clear()
            allItems.addAll(list)
            applyFilter()
        }
    }

    private fun setupChips() {
        val chips = listOf(
            chipAll to "all",
            chipMath to "math",
            chipScience to "science",
            chipCode to "code",
            chipHistory to "history"
        )
        
        for ((chip, subject) in chips) {
            chip.setOnClickListener {
                subjectFilter = subject
                starredOnly = false
                refreshChips()
                applyFilter()
            }
        }
        
        chipStarred.setOnClickListener {
            subjectFilter = "all"
            starredOnly = true
            refreshChips()
            applyFilter()
        }
    }

    private fun refreshChips() {
        styleChip(chipAll, subjectFilter == "all" && !starredOnly)
        styleChip(chipMath, subjectFilter == "math")
        styleChip(chipScience, subjectFilter == "science")
        styleChip(chipCode, subjectFilter == "code")
        styleChip(chipHistory, subjectFilter == "history")
        styleChip(chipStarred, starredOnly)
    }

    private fun styleChip(chip: TextView, isActive: Boolean) {
        if (isActive) {
            chip.setBackgroundResource(R.drawable.bg_history_chip_active)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_primary_foreground))
        } else {
            chip.setBackgroundResource(R.drawable.bg_history_chip)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_foreground))
        }
    }

    private fun applyFilter() {
        val filtered = mutableListOf<QuestionAnswer>()
        for (qa in allItems) {
            if (subjectFilter != "all" && subjectFilter != qa.subject?.lowercase()) {
                continue
            }
            if (starredOnly && !qa.starred) {
                continue
            }
            if (query.isNotEmpty() && qa.question?.lowercase()?.contains(query.lowercase()) != true) {
                continue
            }
            filtered.add(qa)
        }
        adapter.submitList(filtered)
        val empty = filtered.isEmpty()
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        rvHistory.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun subjectLabel(subject: String?): String {
        return when (subject?.lowercase()) {
            "math" -> getString(R.string.subject_math)
            "science" -> getString(R.string.subject_science)
            "code" -> getString(R.string.subject_code)
            "history" -> getString(R.string.subject_history)
            else -> getString(R.string.subject_unknown)
        }
    }

    private fun relativeTime(createdAt: Long): String {
        val diff = System.currentTimeMillis() - createdAt
        val hours = diff / 3_600_000L
        if (hours < 1) {
            return getString(R.string.history_just_now)
        }
        if (hours < 24) {
            return getString(R.string.history_hours_ago, hours.toInt())
        }
        return getString(R.string.history_days_ago, (hours / 24).toInt())
    }
}