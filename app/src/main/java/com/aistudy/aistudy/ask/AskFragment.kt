package com.aistudy.aistudy.ask

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AskFragment : Fragment() {

    companion object {
        private const val TAG = "AskFragment"
        const val XP_PER_QUESTION = 50
        private val VALID_SUBJECTS = setOf("math", "science", "code", "history", "language", "other")

        private fun maskKey(key: String?): String {
            if (key.isNullOrEmpty()) return "null"
            if (key.length <= 8) return "***"
            return key.substring(0, 4) + "..." + key.substring(key.length - 4)
        }

        private fun encodeFileBase64(file: File, fallbackMime: String?): Pair<String, String> {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val maxDim = 1280
            val needsResize = opts.outWidth > maxDim || opts.outHeight > maxDim || file.length() > 1_500_000L
            if (needsResize && opts.outWidth > 0 && opts.outHeight > 0) {
                var sample = 1
                while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                if (bitmap != null) {
                    ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        bitmap.recycle()
                        return Pair(
                            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
                            "image/jpeg"
                        )
                    }
                }
            }
            FileInputStream(file).use { input ->
                ByteArrayOutputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n = input.read(buf)
                    while (n >= 0) {
                        out.write(buf, 0, n)
                        n = input.read(buf)
                    }
                    return Pair(
                        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
                        fallbackMime ?: "image/jpeg"
                    )
                }
            }
        }
    }

    private lateinit var editQuestion: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var btnCamera: MaterialButton
    private lateinit var btnGallery: MaterialButton
    private lateinit var progressLoading: ProgressBar
    private lateinit var layoutImagePreview: LinearLayout
    private lateinit var imgPreview: ImageView

    private var executor: ExecutorService? = null
    private var selectedImageFile: File? = null
    private var selectedMimeType: String? = null
    private var cameraUri: Uri? = null
    private var cameraFile: File? = null

    private lateinit var requestCameraPermission: ActivityResultLauncher<String>
    private lateinit var requestGalleryPermission: ActivityResultLauncher<String>
    private lateinit var takePicture: ActivityResultLauncher<Uri>
    private lateinit var pickImage: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCameraPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                launchCamera()
            } else {
                context?.let {
                    Toast.makeText(it, R.string.ask_permission_camera, Toast.LENGTH_SHORT).show()
                }
            }
        }

        requestGalleryPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                pickImage.launch("image/*")
            } else {
                context?.let {
                    Toast.makeText(it, R.string.ask_permission_gallery, Toast.LENGTH_SHORT).show()
                }
            }
        }

        takePicture = registerForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { success ->
            val file = cameraFile
            if (success && file != null && file.exists()) {
                setSelectedImage(file, "image/jpeg")
            } else {
                context?.let {
                    Toast.makeText(it, R.string.ask_image_error, Toast.LENGTH_SHORT).show()
                }
            }
        }

        pickImage = registerForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            val ctx = context ?: return@registerForActivityResult
            try {
                val copied = copyUriToQaImages(uri)
                var mime = ctx.contentResolver.getType(uri)
                if (mime == null || !mime.startsWith("image/")) {
                    mime = "image/jpeg"
                }
                setSelectedImage(copied, mime)
            } catch (e: Exception) {
                Log.e(TAG, "copy gallery image failed", e)
                Toast.makeText(ctx, R.string.ask_image_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_ask, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        executor = Executors.newSingleThreadExecutor()

        editQuestion = view.findViewById(R.id.edit_question)
        btnSend = view.findViewById(R.id.btn_send)
        btnCamera = view.findViewById(R.id.btn_camera)
        btnGallery = view.findViewById(R.id.btn_gallery)
        progressLoading = view.findViewById(R.id.progress_loading)
        layoutImagePreview = view.findViewById(R.id.layout_image_preview)
        imgPreview = view.findViewById(R.id.img_preview)

        val btnRemoveImage: MaterialButton = view.findViewById(R.id.btn_remove_image)
        btnRemoveImage.setOnClickListener {
            clearSelectedImage()
        }

        btnCamera.setOnClickListener {
            onCameraClick()
        }

        btnGallery.setOnClickListener {
            onGalleryClick()
        }

        btnSend.setOnClickListener {
            onSendClick()
        }

        val btnSuggestion1: MaterialButton = view.findViewById(R.id.btn_suggestion_1)
        val btnSuggestion2: MaterialButton = view.findViewById(R.id.btn_suggestion_2)
        val btnSuggestion3: MaterialButton = view.findViewById(R.id.btn_suggestion_3)
        val btnSuggestion4: MaterialButton = view.findViewById(R.id.btn_suggestion_4)

        btnSuggestion1.setOnClickListener { editQuestion.setText(R.string.ask_suggestion_1) }
        btnSuggestion2.setOnClickListener { editQuestion.setText(R.string.ask_suggestion_2) }
        btnSuggestion3.setOnClickListener { editQuestion.setText(R.string.ask_suggestion_3) }
        btnSuggestion4.setOnClickListener { editQuestion.setText(R.string.ask_suggestion_4) }
    }

    private fun onCameraClick() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val cacheDir = File(requireContext().cacheDir, "camera")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            cameraFile = file
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file,
            )
            cameraUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            Log.e(TAG, "launchCamera failed", e)
            Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onGalleryClick() {
        pickImage.launch("image/*")
    }

    private fun copyUriToQaImages(uri: Uri): File {
        val dir = File(requireContext().filesDir, "qa_images")
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, "qa_${System.currentTimeMillis()}.jpg")
        requireContext().contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IllegalStateException("Cannot open input stream for $uri")
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(8192)
                var n = input.read(buf)
                while (n >= 0) {
                    output.write(buf, 0, n)
                    n = input.read(buf)
                }
            }
        }
        return dest
    }

    private fun setSelectedImage(file: File, mimeType: String) {
        val permanent = copyToPermanentQaImagesIfNeeded(file)
        selectedImageFile = permanent
        selectedMimeType = mimeType

        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeFile(permanent.absolutePath, opts)
        var sample = 1
        val maxDim = 1024
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options()
        decodeOpts.inSampleSize = sample
        val bmp = BitmapFactory.decodeFile(permanent.absolutePath, decodeOpts)
        imgPreview.setImageBitmap(bmp)
        layoutImagePreview.visibility = View.VISIBLE
    }

    private fun copyToPermanentQaImagesIfNeeded(file: File): File {
        val qaDir = File(requireContext().filesDir, "qa_images")
        if (file.parentFile?.absolutePath == qaDir.absolutePath) {
            return file
        }
        if (!qaDir.exists()) qaDir.mkdirs()
        val dest = File(qaDir, "qa_${System.currentTimeMillis()}.jpg")
        FileInputStream(file).use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(8192)
                var n = input.read(buf)
                while (n >= 0) {
                    output.write(buf, 0, n)
                    n = input.read(buf)
                }
            }
        }
        return dest
    }

    private fun clearSelectedImage() {
        selectedImageFile = null
        selectedMimeType = null
        imgPreview.setImageDrawable(null)
        layoutImagePreview.visibility = View.GONE
    }

    private fun onSendClick() {
        val questionInput = editQuestion.text.toString().trim()
        val file = selectedImageFile
        if (questionInput.isEmpty() && file == null) {
            Toast.makeText(requireContext(), R.string.ask_empty_both, Toast.LENGTH_SHORT).show()
            return
        }

        val questionFinal = if (questionInput.isEmpty()) {
            getString(R.string.ask_image_default_question)
        } else {
            questionInput
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        Log.d(TAG, "apiKey=${maskKey(apiKey)}")
        if (apiKey.isEmpty() || apiKey == "YOUR_REAL_GEMINI_API_KEY" || apiKey == "YOUR_KEY_HERE") {
            Toast.makeText(requireContext(), R.string.ask_no_api_key, Toast.LENGTH_LONG).show()
            return
        }

        val prefs = requireActivity().getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) return

        val db = AppDatabase.getInstance(requireContext())
        val profileDao = db.profileDao()
        val qaDao = db.questionAnswerDao()

        val profile = profileDao.getByUserId(userId)
        val level = profile?.level ?: ProfileDefaults.LEVEL_HIGH
        val style = profile?.style ?: ProfileDefaults.STYLE_STEPWISE

        val prompt = buildPrompt(level, style, questionFinal)

        val fallbackMime = selectedMimeType
        val imagePath = file?.absolutePath

        setLoading(true)

        executor?.execute {
            try {
                val encodedImage = if (file != null && file.exists()) {
                    encodeFileBase64(file, fallbackMime)
                } else {
                    null
                }
                val rawResponse = GeminiClient.ask(
                    apiKey = apiKey,
                    prompt = prompt,
                    imageBase64 = encodedImage?.first,
                    mimeType = encodedImage?.second,
                )
                Log.d(TAG, "rawResponse length=${rawResponse.length}")

                val jsonText = extractJson(rawResponse)
                Log.d(TAG, "extracted json text length=${jsonText.length}")
                val json = JSONObject(jsonText)

                var subject = json.optString("subject", "other").lowercase()
                if (!isValidSubject(subject)) {
                    Log.w(TAG, "invalid subject from AI: $subject, fallback to other")
                    subject = "other"
                }

                var difficulty = json.optString("difficulty", "medium")
                if (!isValidDifficulty(difficulty)) {
                    difficulty = "medium"
                }

                val stepsArray = json.getJSONArray("steps")
                val finalAnswer = json.getString("finalAnswer")

                Log.d(
                    TAG, "parsed subject=$subject difficulty=$difficulty" +
                        " steps=${stepsArray.length()}" +
                        " finalAnswerLength=${finalAnswer.length}",
                )

                val qa = QuestionAnswer()
                qa.userId = userId
                qa.subject = subject
                qa.question = questionFinal
                qa.steps = stepsArray.toString()
                qa.finalAnswer = finalAnswer
                qa.starred = false
                qa.createdAt = System.currentTimeMillis()
                qa.difficulty = difficulty
                qa.extrasJson = null
                qa.reviewedAt = 0
                qa.reviewedSeconds = 0
                qa.imagePath = imagePath

                val qaId = qaDao.insert(qa)
                Log.d(TAG, "saved qaId=$qaId imagePath=$imagePath")

                val freshProfile = profileDao.getByUserId(userId)
                if (freshProfile != null) {
                    XpRules.recordDailyActivity(freshProfile, profileDao)
                    XpRules.addXp(freshProfile, profileDao, XpRules.ASK)
                }

                val act = activity ?: return@execute
                act.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    setLoading(false)
                    clearSelectedImage()
                    editQuestion.setText("")

                    val intent = Intent(act, AnswerActivity::class.java)
                    intent.putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
                    act.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ask failed", e)
                val act = activity ?: return@execute
                act.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    setLoading(false)
                    val ctx = context ?: return@runOnUiThread
                    Toast.makeText(
                        ctx,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun isValidSubject(subject: String): Boolean {
        return VALID_SUBJECTS.contains(subject)
    }

    private fun isValidDifficulty(difficulty: String): Boolean {
        return difficulty == "easy" ||
            difficulty == "medium" ||
            difficulty == "hard"
    }

    private fun buildPrompt(level: String, style: String, question: String): String {
        val levelText = when (level) {
            ProfileDefaults.LEVEL_JUNIOR -> "middle school student"
            ProfileDefaults.LEVEL_UNI -> "university student"
            else -> "high school student"
        }
        val styleText = when (style) {
            ProfileDefaults.STYLE_SHORT -> "Keep explanations concise and to the point."
            ProfileDefaults.STYLE_DETAILED -> "Provide detailed explanations with extra context and examples."
            else -> "Explain step by step with clear reasoning for each step."
        }
        return "You are AI Study Mentor, a study tutor in English for a $levelText.\n" +
            styleText + "\n" +
            "Always answer in English. Put math formulas in the \"expr\" field of the matching step.\n" +
            "Classify the subject correctly (math/science/code/history/language/other).\n" +
            "Also rate difficulty as easy, medium, or hard for this student.\n" +
            "If an image is attached, read the problem from the image carefully.\n\n" +
            "Return ONLY valid JSON (no markdown) with this structure:\n" +
            "{\n" +
            "  \"subject\": \"math|science|code|history|language|other\",\n" +
            "  \"difficulty\": \"easy|medium|hard\",\n" +
            "  \"steps\": [{\"title\": \"optional\", \"body\": \"required\", \"expr\": \"optional\"}],\n" +
            "  \"finalAnswer\": \"string\"\n" +
            "}\n\n" +
            "Question: " + question
    }

    private fun extractJson(raw: String): String {
        val text = raw.trim()
        if (text.startsWith("```")) {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1)
            }
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1)
        }
        return text
    }

    private fun setLoading(loading: Boolean) {
        btnSend.isEnabled = !loading
        btnCamera.isEnabled = !loading
        btnGallery.isEnabled = !loading
        btnSend.setText(if (loading) R.string.ask_sending else R.string.ask_send)
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        executor?.shutdown()
        super.onDestroyView()
    }
}
