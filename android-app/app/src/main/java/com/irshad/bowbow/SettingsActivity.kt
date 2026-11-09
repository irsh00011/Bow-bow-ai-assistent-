package com.irshad.bowbow

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.irshad.bowbow.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Settings: backend URL, voice language, spoken replies, version. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        binding.backendUrlInput.setText(Prefs.getBackendUrl(this))

        when (Prefs.getLanguage(this)) {
            "ta-IN" -> binding.languageGroup.check(R.id.languageTamil)
            "en-IN" -> binding.languageGroup.check(R.id.languageEnglish)
            else -> binding.languageGroup.check(R.id.languageAuto)
        }

        binding.ttsSwitch.isChecked = Prefs.isTtsEnabled(this)

        binding.saveButton.setOnClickListener {
            val url = binding.backendUrlInput.text.toString().trim().trimEnd('/')
            if (url.isNotBlank()) {
                Prefs.setBackendUrl(this, url)
            }
            val language = when (binding.languageGroup.checkedRadioButtonId) {
                R.id.languageTamil -> "ta-IN"
                R.id.languageEnglish -> "en-IN"
                else -> "auto"
            }
            Prefs.setLanguage(this, language)
            Prefs.setTtsEnabled(this, binding.ttsSwitch.isChecked)
            Toast.makeText(this, R.string.backend_url_saved, Toast.LENGTH_SHORT)
                .show()
            finish()
        }

        binding.backendStatusButton.setOnClickListener { checkBackendStatus() }

        binding.versionText.text =
            "${getString(R.string.version_label)} ${BuildConfig.VERSION_NAME} " +
                "(${BuildConfig.VERSION_CODE})"
    }

    private fun checkBackendStatus() {
        binding.backendStatusText.text = getString(R.string.loading)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url("${Prefs.getBackendUrl(this@SettingsActivity)}/health")
                        .get()
                        .build()
                    client.newCall(request).execute().use { it.isSuccessful }
                } catch (e: Exception) {
                    false
                }
            }
            binding.backendStatusText.text = getString(
                if (ok) R.string.backend_status_ok else R.string.backend_status_failed
            )
        }
    }
}
