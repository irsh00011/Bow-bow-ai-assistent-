package com.irshad.bowbow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.irshad.bowbow.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

/** BOW BOW — dark voice assistant.
 *  Tap the mic, speak in Tamil / English / Tanglish, hear the reply.
 *  Text input works as a fallback. History and Settings live in the
 *  top bar; the phone icon requests an announcement call (confirmed
 *  in-app, then placed by the backend to the user's own number). */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ChatAdapter
    private var api: BowBowApi? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var isBusy = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = results[Manifest.permission.RECORD_AUDIO] == true
        binding.micButton.isEnabled = audioGranted
        if (!audioGranted) {
            Toast.makeText(
                this, R.string.mic_permission_needed, Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // BOW BOW is always dark.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        api = BowBowApi(Prefs.getBackendUrl(this))

        adapter = ChatAdapter()
        binding.chatList.layoutManager = LinearLayoutManager(this)
        binding.chatList.adapter = adapter

        binding.micButton.setOnClickListener { onMicTapped() }
        binding.sendButton.setOnClickListener { sendTypedMessage() }
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTypedMessage()
                true
            } else {
                false
            }
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.callButton.setOnClickListener { showCallDialog() }

        initTextToSpeech()
        requestPermissions()
        scheduleReminderChecks()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    // ----- Voice input -----

    private fun recognizerLanguage(): String =
        when (Prefs.getLanguage(this)) {
            "en-IN" -> "en-IN"
            else -> "ta-IN" // "auto" and "ta-IN" both start in Tamil
        }

    private fun onMicTapped() {
        if (isBusy) return
        if (isListening) {
            stopListening()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(
                this, R.string.speech_not_available, Toast.LENGTH_LONG
            ).show()
            return
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions()
            return
        }
        startListening()
    }

    private fun startListening() {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                setStatus(getString(R.string.listening))
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                binding.waveformView.setLevel(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                Log.w(TAG, "Speech recognition error: $error")
                stopListening()
                setStatus(getString(R.string.tap_mic_hint))
            }

            override fun onResults(results: Bundle?) {
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                stopListening()
                setStatus(getString(R.string.tap_mic_hint))
                if (heard.isNotBlank()) sendMessage(heard)
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognizerLanguage())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        isListening = true
        binding.waveformView.startAnimation()
        binding.micButton.setImageResource(android.R.drawable.ic_media_pause)
        recognizer.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        binding.waveformView.stopAnimation()
        binding.micButton.setImageResource(android.R.drawable.ic_btn_speak_now)
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // ----- Text-to-speech -----

    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            val wanted = when (Prefs.getLanguage(this)) {
                "en-IN" -> Locale("en", "IN")
                else -> Locale("ta", "IN")
            }
            engine.language =
                if (engine.isLanguageAvailable(wanted) >= TextToSpeech.LANG_AVAILABLE) {
                    wanted
                } else {
                    Locale("en", "IN")
                }
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread {
                            setStatus(getString(R.string.speaking))
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            setStatus(getString(R.string.tap_mic_hint))
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            setStatus(getString(R.string.tap_mic_hint))
                        }
                    }
                }
            )
        }
    }

    private fun speak(text: String) {
        if (!Prefs.isTtsEnabled(this)) return
        tts?.speak(
            text, TextToSpeech.QUEUE_FLUSH, null,
            "bowbow-${System.currentTimeMillis()}"
        )
    }

    // ----- Chat -----

    private fun sendTypedMessage() {
        val text = binding.messageInput.text.toString().trim()
        if (text.isEmpty()) return
        binding.messageInput.text.clear()
        sendMessage(text)
    }

    private fun sendMessage(text: String) {
        if (isBusy) return
        isBusy = true
        adapter.addMessage(ChatMessage(text, isUser = true))
        scrollToBottom()
        setStatus(getString(R.string.thinking))

        lifecycleScope.launch {
            try {
                val client = api ?: BowBowApi(Prefs.getBackendUrl(this@MainActivity))
                val reply = client.chat(text, "auto", BowBowConfig.USER_ID)
                adapter.addMessage(ChatMessage(reply, isUser = false))
                scrollToBottom()
                speak(reply)
            } catch (e: Exception) {
                Log.w(TAG, "Chat failed: ${e.message}")
                adapter.addMessage(
                    ChatMessage(getString(R.string.could_not_reach_server), isUser = false)
                )
                scrollToBottom()
            } finally {
                isBusy = false
                setStatus(getString(R.string.tap_mic_hint))
            }
        }
    }

    private fun scrollToBottom() {
        val count = adapter.itemCount
        if (count > 0) binding.chatList.scrollToPosition(count - 1)
    }

    private fun setStatus(text: String) {
        binding.statusText.text = text
    }

    // ----- Phone call request (confirmed in-app, placed by backend) -----

    private fun showCallDialog() {
        val numberInput = EditText(this).apply {
            setText("+917418189588")
            hint = getString(R.string.call_number_hint)
            inputType =
                android.text.InputType.TYPE_CLASS_PHONE
        }
        val messageInput = EditText(this).apply {
            setText("Hi, this is BOW BOW, your personal assistant calling.")
            hint = getString(R.string.call_message_hint)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
            addView(numberInput)
            addView(messageInput)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.call_title)
            .setView(layout)
            .setPositiveButton(R.string.call_confirm) { _, _ ->
                val to = numberInput.text.toString().trim()
                val message = messageInput.text.toString().trim()
                if (to.isNotBlank() && message.isNotBlank()) {
                    placeCall(to, message)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun placeCall(to: String, message: String) {
        lifecycleScope.launch {
            try {
                val client = api ?: BowBowApi(Prefs.getBackendUrl(this@MainActivity))
                client.requestCall(to, message)
                Toast.makeText(
                    this@MainActivity, R.string.call_placed, Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.w(TAG, "Call failed: ${e.message}")
                Toast.makeText(
                    this@MainActivity, R.string.call_failed, Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ----- Permissions & reminder polling -----

    private fun requestPermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            binding.micButton.isEnabled = true
        }
    }

    /** Schedule the 15-minute reminder check (WorkManager).
     *  No server push: the app polls the backend itself and shows
     *  local notifications. KEEP policy means re-launching the app
     *  never schedules a second job. */
    private fun scheduleReminderChecks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val TAG = "BowBow"
    }
}
