package com.aistudy.aistudy.history

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private lateinit var recyclerHistory: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var chipAll: TextView
    private lateinit var chipMath: TextView
    private lateinit var chipScience: TextView
    private lateinit var chipCode: TextView
    private lateinit var chipHistory: TextView
    private lateinit var chipLanguage: TextView
    private lateinit var chipStarred: TextView

    private var subjectFilter: String? = null
    private var starredOnly = false

    private var allList: List<QuestionAnswer> = emptyList()
    private val displayList = mutableListOf<QuestionAnswer>()
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerHistory = view.findViewById(R.id.recycler_history)
        tvEmpty = view.findViewById(R.id.tv_empty)

        chipAll = view.findViewById(R.id.chip_all)
        chipMath = view.findViewById(R.id.chip_math)
        chipScience = view.findViewById(R.id.chip_science)
        chipCode = view.findViewById(R.id.chip_code)
        chipHistory = view.findViewById(R.id.chip_history)
        chipLanguage = view.findViewById(R.id.chip_language)
        chipStarred = view.findViewById(R.id.chip_starred)

        adapter = HistoryAdapter(displayList, { qa ->
            val intent = Intent(requireContext(), AnswerActivity::class.java)
            intent.putExtra(AnswerActivity.EXTRA_QA_ID, qa.id)
            startActivity(intent)
        }, { qa ->
            val db = AppDatabase.getInstance(requireContext())
            db.questionAnswerDao().toggleStar(qa.id)
            loadData()
        })

        recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        recyclerHistory.adapter = adapter

        chipAll.setOnClickListener {
            subjectFilter = null
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipMath.setOnClickListener {
            subjectFilter = "math"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipScience.setOnClickListener {
            subjectFilter = "science"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipCode.setOnClickListener {
            subjectFilter = "code"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipHistory.setOnClickListener {
            subjectFilter = "history"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipLanguage.setOnClickListener {
            subjectFilter = "language"
            starredOnly = false
            refreshChips()
            applyFilter()
        }
        chipStarred.setOnClickListener {
            starredOnly = true
            subjectFilter = null
            refreshChips()
            applyFilter()
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val context = context ?: return
        val userId = requireActivity().getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) return

        val db = AppDatabase.getInstance(context)
        allList = db.questionAnswerDao().getByUserId(userId)
        applyFilter()
    }

    private fun applyFilter() {
        displayList.clear()
        for (item in allList) {
            if (starredOnly && !item.starred) continue
            if (subjectFilter != null && item.subject != subjectFilter) continue
            displayList.add(item)
        }
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (displayList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshChips() {
        styleChip(chipAll, subjectFilter == null && !starredOnly)
        styleChip(chipMath, subjectFilter == "math" && !starredOnly)
        styleChip(chipScience, subjectFilter == "science" && !starredOnly)
        styleChip(chipCode, subjectFilter == "code" && !starredOnly)
        styleChip(chipHistory, subjectFilter == "history" && !starredOnly)
        styleChip(chipLanguage, subjectFilter == "language" && !starredOnly)
        styleChip(chipStarred, starredOnly)
    }

    private fun styleChip(chip: TextView, active: Boolean) {
        if (active) {
            chip.setBackgroundResource(R.drawable.bg_answer_action_active)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_primary_foreground))
        } else {
            chip.setBackgroundResource(R.drawable.bg_history_chip)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.auth_foreground))
        }
    }

    private class HistoryAdapter(
        private val list: List<QuestionAnswer>,
        private val onItemClick: (QuestionAnswer) -> Unit,
        private val onStarClick: (QuestionAnswer) -> Unit,
    ) : RecyclerView.Adapter<HistoryViewHolder>() {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_question_answer, parent, false)
            return HistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val item = list[position]
            holder.tvSubject.text = item.subject?.uppercase() ?: "OTHER"
            holder.tvDifficulty.text = item.difficulty?.uppercase() ?: "MEDIUM"
            holder.tvQuestion.text = item.question
            holder.tvDate.text = dateFormat.format(Date(item.createdAt))

            val starIcon = if (item.starred) {
                android.R.drawable.btn_star_big_on
            } else {
                android.R.drawable.btn_star_big_off
            }
            holder.btnStar.setImageResource(starIcon)

            holder.itemView.setOnClickListener { onItemClick(item) }
            holder.btnStar.setOnClickListener { onStarClick(item) }
        }

        override fun getItemCount(): Int = list.size
    }

    private class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tv_subject)
        val tvDifficulty: TextView = view.findViewById(R.id.tv_difficulty)
        val tvQuestion: TextView = view.findViewById(R.id.tv_question)
        val tvDate: TextView = view.findViewById(R.id.tv_date)
        val btnStar: ImageButton = view.findViewById(R.id.btn_star)
    }
}
