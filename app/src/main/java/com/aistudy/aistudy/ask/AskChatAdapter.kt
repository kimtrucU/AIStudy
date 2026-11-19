package com.aistudy.aistudy.ask

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String = "",
    val imagePath: String? = null,
    val isLoading: Boolean = false,
    val errorText: String? = null,
    val qaId: Long = -1L,
    val subject: String = "other",
    val difficulty: String = "medium",
    val stepsJson: String = "[]",
    val finalAnswer: String = ""
)

class AskChatAdapter(
    private val items: MutableList<ChatMessage>,
    private val onOpenDetails: (Long) -> Unit,
    private val subjectLabelProvider: (String) -> String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            AiHolder(inflater.inflate(R.layout.item_chat_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        if (holder is UserHolder) {
            holder.bind(msg)
        } else if (holder is AiHolder) {
            holder.bind(msg)
        }
    }

    override fun getItemCount(): Int = items.size

    class UserHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgUser: ImageView = itemView.findViewById(R.id.img_chat_user)
        private val tvText: TextView = itemView.findViewById(R.id.tv_chat_user_text)

        fun bind(msg: ChatMessage) {
            tvText.text = msg.text
            val path = msg.imagePath
            if (!path.isNullOrEmpty() && File(path).exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeFile(path, opts)
                if (bmp != null) {
                    imgUser.setImageBitmap(bmp)
                    imgUser.visibility = View.VISIBLE
                } else {
                    imgUser.visibility = View.GONE
                }
            } else {
                imgUser.visibility = View.GONE
            }
        }
    }

    inner class AiHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMeta: TextView = itemView.findViewById(R.id.tv_chat_meta)
        private val layoutThinking: View = itemView.findViewById(R.id.layout_chat_thinking)
        private val tvError: TextView = itemView.findViewById(R.id.tv_chat_error)
        private val layoutContent: View = itemView.findViewById(R.id.layout_chat_content)
        private val layoutSteps: LinearLayout = itemView.findViewById(R.id.layout_chat_steps)
        private val tvFinalAnswer: TextView = itemView.findViewById(R.id.tv_chat_final_answer)
        private val btnDetails: TextView = itemView.findViewById(R.id.btn_chat_details)
        private val btnCopy: TextView = itemView.findViewById(R.id.btn_chat_copy)

        fun bind(msg: ChatMessage) {
            if (msg.isLoading) {
                tvMeta.visibility = View.GONE
                layoutThinking.visibility = View.VISIBLE
                tvError.visibility = View.GONE
                layoutContent.visibility = View.GONE
                return
            }

            if (!msg.errorText.isNullOrEmpty()) {
                tvMeta.visibility = View.GONE
                layoutThinking.visibility = View.GONE
                tvError.visibility = View.VISIBLE
                tvError.text = msg.errorText
                layoutContent.visibility = View.GONE
                return
            }

            layoutThinking.visibility = View.GONE
            tvError.visibility = View.GONE
            layoutContent.visibility = View.VISIBLE

            val subjLabel = subjectLabelProvider(msg.subject)
            val diffLabel = msg.difficulty.replaceFirstChar { it.uppercase() }
            tvMeta.text = "$subjLabel • $diffLabel"
            tvMeta.visibility = View.VISIBLE

            layoutSteps.removeAllViews()
            val ctx = itemView.context
            try {
                val steps = JSONArray(msg.stepsJson)
                for (i in 0 until steps.length()) {
                    addStepView(ctx, layoutSteps, i + 1, steps.getJSONObject(i))
                }
            } catch (_: Exception) {
                if (msg.stepsJson.isNotEmpty() && msg.stepsJson != "[]") {
                    val tv = TextView(ctx).apply {
                        text = msg.stepsJson
                        setTextColor(ContextCompat.getColor(ctx, R.color.auth_foreground))
                        textSize = 14f
                    }
                    layoutSteps.addView(tv)
                }
            }

            tvFinalAnswer.text = msg.finalAnswer

            btnDetails.visibility = if (msg.qaId > 0) View.VISIBLE else View.GONE
            btnDetails.setOnClickListener {
                if (msg.qaId > 0) {
                    onOpenDetails(msg.qaId)
                }
            }

            btnCopy.setOnClickListener {
                val fullText = buildCopyText(msg)
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AIStudy Answer", fullText))
                Toast.makeText(ctx, "Copied answer", Toast.LENGTH_SHORT).show()
            }
        }

        private fun buildCopyText(msg: ChatMessage): String {
            val sb = StringBuilder()
            try {
                val steps = JSONArray(msg.stepsJson)
                for (i in 0 until steps.length()) {
                    val s = steps.getJSONObject(i)
                    val title = s.optString("title", "")
                    val body = s.optString("body", "")
                    val expr = s.optString("expr", "")
                    sb.append("${i + 1}. ")
                    if (title.isNotEmpty()) sb.append("$title: ")
                    sb.append(body)
                    if (expr.isNotEmpty()) sb.append("\n   $expr")
                    sb.append("\n\n")
                }
            } catch (_: Exception) {
            }
            sb.append("Final Answer: ${msg.finalAnswer}")
            return sb.toString().trim()
        }

        private fun addStepView(
            ctx: Context,
            parent: LinearLayout,
            number: Int,
            step: JSONObject
        ) {
            val density = ctx.resources.displayMetrics.density
            val stepNumber = TextView(ctx).apply {
                text = number.toString()
                gravity = Gravity.CENTER
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.auth_primary_foreground))
                setBackgroundResource(R.drawable.bg_ask_send_circle)
                val size = (22 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(0, (2 * density).toInt(), (10 * density).toInt(), 0)
                }
            }

            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    bottomMargin = (12 * density).toInt()
                }
            }

            val title = step.optString("title", "")
            if (title.isNotEmpty()) {
                content.addView(TextView(ctx).apply {
                    text = title
                    setTextColor(ContextCompat.getColor(ctx, R.color.auth_foreground))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                })
            }

            val body = step.optString("body", "")
            if (body.isNotEmpty()) {
                content.addView(TextView(ctx).apply {
                    text = body
                    setTextColor(ContextCompat.getColor(ctx, R.color.auth_foreground))
                    textSize = 14f
                    setLineSpacing(0f, 1.15f)
                })
            }

            val expr = step.optString("expr", "")
            if (expr.isNotEmpty()) {
                content.addView(TextView(ctx).apply {
                    text = expr
                    setTextColor(ContextCompat.getColor(ctx, R.color.auth_foreground))
                    textSize = 13f
                    setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
                    setPadding(
                        (10 * density).toInt(),
                        (6 * density).toInt(),
                        (10 * density).toInt(),
                        (6 * density).toInt()
                    )
                    setBackgroundResource(R.drawable.bg_auth_input)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (6 * density).toInt()
                    }
                })
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(stepNumber)
                addView(content)
            }
            parent.addView(row)
        }
    }
}
