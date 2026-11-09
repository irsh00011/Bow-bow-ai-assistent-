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
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

/** BOW BOW — single-screen voice assistant.
 *  Tap the mic, speak in Tamil / English / Tanglish, hear the reply.
 *  Text input works as a fallback. The backend URL is changeable in
 *  Settings (gear icon, top right). */
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
        binding.settingsButton.setOnClickListener { showSettingsDialog() }

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
            override fun onRmsChanged(rmsdB: Float) = Unit
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
            // Tamil first — the recognizer falls back to the phone's
            // default language when Tamil is unavailable.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        isListening = true
        recognizer.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // ----- Text-to-speech -----

    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            val tamil = Locale("ta", "IN")
            engine.language =
                if (engine.isLanguageAvailable(tamil) >= TextToSpeech.LANG_AVAILABLE) {
                    tamil
                } else {
                    Locale("en", "IN")
                }
        }
    }

    private fun speak(text: String) {
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

    // ----- Settings -----

    private fun showSettingsDialog() {
        val input = EditText(this).apply {
            setText(Prefs.getBackendUrl(this@MainActivity))
            hint = BowBowConfig.DEFAULT_BACKEND_URL
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setMessage(R.string.backend_url_hint)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text.toString().trim().trimEnd('/')
                if (url.isNotBlank()) {
                    Prefs.setBackendUrl(this, url)
                    api = BowBowApi(url)
                    Toast.makeText(
                        this, R.string.backend_url_saved, Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
