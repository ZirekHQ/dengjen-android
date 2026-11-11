package io.github.zirekhq.dengjen.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.format.Formatter
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.zirekhq.dengjen.R
import io.github.zirekhq.dengjen.tts.Languages
import io.github.zirekhq.dengjen.voices.CatalogueVoice
import io.github.zirekhq.dengjen.voices.InstallState
import io.github.zirekhq.dengjen.voices.VoiceRepository

/** The voice manager: downloads and removes Kurmanji voices and picks the default one. */
class MainActivity : Activity() {
    private lateinit var repository: VoiceRepository
    private lateinit var status: TextView
    private lateinit var retry: Button
    private lateinit var list: LinearLayout
    private var loading = true
    private var loadFailed = false
    private var tts: TextToSpeech? = null
    private val previous = mutableMapOf<String, InstallState>()
    private val rows = linkedMapOf<String, View>()
    private val listener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repository = VoiceRepository.get(this)
        status = findViewById(R.id.status)
        retry = findViewById(R.id.retry)
        list = findViewById(R.id.voice_list)
        applyInsets(findViewById(R.id.root))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            findViewById<TextView>(R.id.voices_heading).isAccessibilityHeading = true
        }
        findViewById<Button>(R.id.open_tts_settings).setOnClickListener { openTtsSettings() }
        findViewById<Button>(R.id.test_voice).setOnClickListener { speakSample() }
        retry.setOnClickListener { load() }
        load()
    }

    override fun onStart() {
        super.onStart()
        repository.addListener(listener)
        render()
    }

    override fun onStop() {
        repository.removeListener(listener)
        super.onStop()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun load() {
        loading = true
        loadFailed = false
        render()
        repository.loadCatalogue { ok ->
            loading = false
            loadFailed = !ok
            render()
        }
    }

    private fun render() {
        val voices = repository.kurmanjiVoices()
        val installed = repository.installed()
        retry.visibility = if (loadFailed) View.VISIBLE else View.GONE
        val text = when {
            loading && voices.isEmpty() -> getString(R.string.status_loading)
            loadFailed && voices.isEmpty() -> getString(R.string.status_offline)
            else -> getString(R.string.status_ready, voices.size, voices.count { it.key in installed })
        }
        if (status.text.toString() != text) status.text = text
        // Rows are rebuilt only when the voice list changes; progress updates edit them in place so
        // TalkBack keeps its focus on the control the user is on.
        if (rows.keys.toList() != voices.map { it.key }) {
            list.removeAllViews()
            rows.clear()
            voices.forEach { voice ->
                val view = layoutInflater.inflate(R.layout.item_voice, list, false)
                rows[voice.key] = view
                list.addView(view)
            }
        }
        val defaultKey = repository.defaultVoice()
        voices.forEach { bind(rows.getValue(it.key), it, it.key in installed, it.key == defaultKey) }
    }

    private fun bind(view: View, voice: CatalogueVoice, installed: Boolean, isDefault: Boolean) {
        val label = voice.name.replaceFirstChar { it.uppercase() }
        view.findViewById<TextView>(R.id.voice_name).setIfChanged(label)
        view.findViewById<TextView>(R.id.voice_detail).setIfChanged(
            getString(R.string.voice_detail, voice.quality, Formatter.formatShortFileSize(this, voice.totalBytes)),
        )
        val state = repository.state(voice.key)
        announceChange(voice.key, label, state, installed)
        view.findViewById<TextView>(R.id.voice_state).setIfChanged(when {
            state is InstallState.Downloading -> getString(R.string.voice_downloading, state.progress.percent)
            state is InstallState.Failed -> getString(R.string.voice_failed, state.reason)
            isDefault -> getString(R.string.voice_default)
            installed -> getString(R.string.voice_installed)
            else -> getString(R.string.voice_not_installed)
        })
        val action = view.findViewById<Button>(R.id.voice_action)
        when {
            state is InstallState.Downloading -> action.setup(R.string.cancel, R.string.cancel_named, label) {
                repository.cancel(voice.key)
            }
            installed -> action.setup(R.string.remove, R.string.remove_named, label) { remove(voice.key, label) }
            else -> action.setup(R.string.install, R.string.install_named, label) { repository.install(voice) }
        }
        val makeDefault = view.findViewById<Button>(R.id.voice_default)
        makeDefault.visibility = if (installed && !isDefault) View.VISIBLE else View.GONE
        makeDefault.contentDescription = getString(R.string.make_default_named, label)
        makeDefault.setOnClickListener { repository.setDefaultVoice(voice.key) }
    }

    private fun TextView.setIfChanged(value: String) {
        if (text.toString() != value) text = value
    }

    /** Spoken feedback for the end of a download, which a screen reader would otherwise miss. */
    private fun announceChange(key: String, label: String, state: InstallState, installed: Boolean) {
        val before = previous.put(key, state)
        if (before is InstallState.Downloading && state !is InstallState.Downloading) {
            val message = when {
                state is InstallState.Failed -> getString(R.string.voice_failed, state.reason)
                installed -> getString(R.string.download_done, label)
                else -> return
            }
            list.announceForAccessibility(message)
        }
    }

    private fun Button.setup(text: Int, description: Int, label: String, onClick: () -> Unit) {
        setIfChanged(getString(text))
        val spoken = getString(description, label)
        if (contentDescription?.toString() != spoken) contentDescription = spoken
        setOnClickListener { onClick() }
    }

    private fun remove(key: String, label: String) {
        repository.remove(key) { error ->
            val message = if (error == null) getString(R.string.removed, label) else getString(R.string.remove_failed, label)
            list.announceForAccessibility(message)
        }
    }

    private fun openTtsSettings() {
        try {
            startActivity(Intent(TTS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun speakSample() {
        if (repository.installed().isEmpty()) {
            status.text = getString(R.string.test_unavailable)
            return
        }
        tts?.let {
            it.speak(getString(R.string.sample_text), TextToSpeech.QUEUE_FLUSH, null, SAMPLE_ID)
            return
        }
        var engine: TextToSpeech? = null
        engine = TextToSpeech(this, { result ->
            val ready = result == TextToSpeech.SUCCESS &&
                engine?.setLanguage(Languages.LOCALE)?.let { it >= TextToSpeech.LANG_AVAILABLE } == true
            if (ready) {
                engine?.speak(getString(R.string.sample_text), TextToSpeech.QUEUE_FLUSH, null, SAMPLE_ID)
            } else {
                status.text = getString(R.string.test_failed)
                engine?.shutdown()
                if (tts === engine) tts = null
            }
        }, packageName)
        tts = engine
    }

    private fun applyInsets(root: View) {
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }
    }

    private companion object {
        const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
        const val SAMPLE_ID = "dengjen-sample"
    }
}
