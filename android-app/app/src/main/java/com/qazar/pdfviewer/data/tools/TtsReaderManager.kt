package com.qazar.pdfviewer.data.tools

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.qazar.pdfviewer.bridge.ExtractedWord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Enterprise-Grade Offline Text-to-Speech (TTS) Engine
 * With real-time word-level highlighting via UtteranceProgressListener.onRangeStart
 * and intelligent fallback word-timing for engines that don't support it.
 */
class TtsReaderManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val TAG = "TtsReaderManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingAutoPlay = false
    var onPageFinished: (() -> Unit)? = null

    private var audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    // Coroutine scope for fallback word timing
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fallbackTimerJob: Job? = null
    @Volatile private var hasReceivedRangeStart = false

    /**
     * Lightweight voice descriptor exposed to the Compose UI layer.
     * Maps 1:1 to an android.speech.tts.Voice but avoids leaking the framework type.
     */
    data class VoiceInfo(
        val name: String,
        val displayName: String,
        val locale: String,
        val isNeural: Boolean,
        val qualityTier: String,
        val qualityScore: Int
    )

    data class TtsState(
        val isPlaying: Boolean = false,
        val isPaused: Boolean = false,
        val currentPageIndex: Int = 0,
        val currentSentenceIndex: Int = 0,
        val totalSentences: Int = 0,
        val currentSentence: String = "",
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.05f,
        val isVisible: Boolean = false,
        // Word-level highlight state — the heart of the real-time sync
        val activeSentenceWords: List<ExtractedWord> = emptyList(),
        val activeWordIndex: Int = -1,
        val activeWord: ExtractedWord? = null,
        // Voice selection state
        val availableVoices: List<VoiceInfo> = emptyList(),
        val selectedVoiceName: String = "",
        val selectedVoiceLabel: String = "Auto (Best)"
    )

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var sentences = listOf<String>()
    private var allWords = listOf<ExtractedWord>()
    private var sentenceWordMap = listOf<List<ExtractedWord>>()
    private val voiceMap = mutableMapOf<String, Voice>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.setSpeechRate(_state.value.speechRate)
            tts?.setPitch(_state.value.pitch)

            try {
                discoverAndRankVoices()
            } catch (e: Exception) {
                Log.w(TAG, "Voice discovery failed, using engine default", e)
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _state.value = _state.value.copy(isPlaying = true, isPaused = false)
                    startFallbackWordTimer()
                }

                override fun onDone(utteranceId: String?) {
                    fallbackTimerJob?.cancel()
                    val nextIdx = _state.value.currentSentenceIndex + 1
                    if (nextIdx < sentences.size) {
                        // Natural breathing pause between sentences for human-like cadence
                        scope.launch {
                            delay(280L)
                            if (!_state.value.isPaused && _state.value.isVisible) {
                                speakSentence(nextIdx)
                            }
                        }
                    } else {
                        _state.value = _state.value.copy(
                            isPlaying = false, isPaused = false,
                            activeWord = null, activeWordIndex = -1,
                            activeSentenceWords = emptyList()
                        )
                        abandonAudioFocus()
                        onPageFinished?.invoke()
                    }
                }

                override fun onError(utteranceId: String?) {
                    fallbackTimerJob?.cancel()
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        activeWord = null, activeWordIndex = -1
                    )
                    abandonAudioFocus()
                }

                // The money shot — millisecond-accurate word offsets straight from the synthesizer
                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    hasReceivedRangeStart = true
                    fallbackTimerJob?.cancel()
                    highlightWordAtCharOffset(start, end)
                }
            })

            isInitialized = true
            Log.i(TAG, "TTS Engine successfully initialized.")
            if (pendingAutoPlay && sentences.isNotEmpty()) {
                pendingAutoPlay = false
                startReading()
            }
        } else {
            Log.e(TAG, "TTS Engine initialization failed (code=$status)")
        }
    }

    /**
     * Feeds text for a page, segments into sentences, maps words spatially.
     * Accepts optional ExtractedWord list from the text extraction engine to enable
     * pixel-precise highlight overlays on the rendered PDF page.
     */
    fun loadPageText(pageIndex: Int, fullText: String, words: List<ExtractedWord> = emptyList()) {
        sentences = segmentSentencesNaturally(fullText)
        allWords = words

        // Build sentence -> word mapping via sequential text matching
        // Words and sentences are both in reading order, so we advance through
        // the word list as we consume sentences. Rock solid for any text source.
        sentenceWordMap = if (words.isNotEmpty()) {
            val mapping = mutableListOf<List<ExtractedWord>>()
            var wordCursor = 0
            for (sentence in sentences) {
                val sentWords = mutableListOf<ExtractedWord>()
                var scanPos = 0

                while (wordCursor < words.size) {
                    val word = words[wordCursor]
                    val wordText = word.text.trim()
                    if (wordText.isEmpty()) {
                        wordCursor++
                        continue
                    }

                    val foundAt = sentence.indexOf(wordText, scanPos, ignoreCase = true)
                    if (foundAt >= 0) {
                        sentWords.add(word)
                        scanPos = foundAt + wordText.length
                        wordCursor++
                    } else {
                        // Try matching without trailing punctuation
                        val stripped = wordText.trimEnd('.', ',', ';', ':', '!', '?', ')', '(', '"', '\'')
                        val foundStripped = if (stripped.isNotEmpty()) sentence.indexOf(stripped, scanPos, ignoreCase = true) else -1
                        if (foundStripped >= 0) {
                            sentWords.add(word)
                            scanPos = foundStripped + stripped.length
                            wordCursor++
                        } else {
                            // Word doesn't belong to this sentence — belongs to next
                            break
                        }
                    }
                }
                mapping.add(sentWords)
            }
            mapping
        } else {
            sentences.map { emptyList() }
        }

        _state.value = _state.value.copy(
            currentPageIndex = pageIndex,
            currentSentenceIndex = 0,
            totalSentences = sentences.size,
            currentSentence = sentences.firstOrNull() ?: "",
            isVisible = true,
            activeSentenceWords = sentenceWordMap.firstOrNull() ?: emptyList(),
            activeWordIndex = -1,
            activeWord = null
        )
    }

    fun startReading() {
        if (!isInitialized) {
            pendingAutoPlay = true
            return
        }
        requestAudioFocus()
        speakSentence(_state.value.currentSentenceIndex)
    }

    fun pauseReading() {
        tts?.stop()
        fallbackTimerJob?.cancel()
        // Keep activeWord highlighted so user sees where we paused
        _state.value = _state.value.copy(isPlaying = false, isPaused = true)
        abandonAudioFocus()
    }

    fun resumeReading() {
        if (_state.value.isPaused) {
            startReading()
        }
    }

    fun nextSentence() {
        val nextIdx = _state.value.currentSentenceIndex + 1
        if (nextIdx < sentences.size) {
            speakSentence(nextIdx)
        }
    }

    fun previousSentence() {
        val prevIdx = (_state.value.currentSentenceIndex - 1).coerceAtLeast(0)
        speakSentence(prevIdx)
    }

    fun setSpeed(rate: Float) {
        val validRate = rate.coerceIn(0.5f, 2.5f)
        tts?.setSpeechRate(validRate)
        _state.value = _state.value.copy(speechRate = validRate)
        if (_state.value.isPlaying) {
            speakSentence(_state.value.currentSentenceIndex)
        }
    }

    fun close() {
        pauseReading()
        _state.value = _state.value.copy(
            isVisible = false,
            activeWord = null, activeWordIndex = -1,
            activeSentenceWords = emptyList()
        )
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        fallbackTimerJob?.cancel()
        scope.cancel()
        abandonAudioFocus()
    }

    /**
     * Discovers all available TTS voices, ranks them by quality and naturalness,
     * and auto-selects the best female neural voice for a silky "Gemini Lady" experience.
     * Preference: Network/Neural female > Network/Neural any > HD female > HD any > Standard
     */
    private fun discoverAndRankVoices() {
        val allVoices = tts?.voices ?: return
        val deviceLocale = Locale.getDefault()

        val ranked = allVoices
            .filter { it.locale.language == deviceLocale.language || it.locale.language == "en" }
            .map { voice ->
                val name = voice.name
                val isNeural = name.contains("network", ignoreCase = true) ||
                        name.contains("neural", ignoreCase = true) ||
                        name.contains("maple", ignoreCase = true) ||
                        voice.quality >= 400

                // Female voice heuristic from Google TTS voice naming conventions
                val isFemale = name.contains("-x-tpf", ignoreCase = true) ||
                        name.contains("-x-iol", ignoreCase = true) ||
                        name.contains("-x-sfg", ignoreCase = true) ||
                        name.contains("-x-kda", ignoreCase = true) ||
                        name.contains("female", ignoreCase = true) ||
                        name.contains("-f-", ignoreCase = true)

                val localeMatch = voice.locale.language == deviceLocale.language

                // Composite quality score (higher = better)
                var score = voice.quality
                if (isNeural) score += 500
                if (isFemale) score += 200  // Prefer female for "Gemini Lady" feel
                if (localeMatch) score += 100
                if (name.contains("network")) score += 300
                if (!voice.isNetworkConnectionRequired) score += 50  // Prefer offline-capable

                val qualityTier = when {
                    isNeural || score >= 800 -> "Neural"
                    voice.quality >= 300 -> "HD"
                    else -> "Standard"
                }

                val localeName = voice.locale.displayName
                val genderHint = when {
                    isFemale -> "\u2640"
                    name.contains("-x-tpd") || name.contains("-x-iom") || name.contains("male", ignoreCase = true) -> "\u2642"
                    else -> ""
                }
                val displayName = "$localeName $genderHint ($qualityTier)".trim()

                VoiceInfo(
                    name = name,
                    displayName = displayName,
                    locale = localeName,
                    isNeural = isNeural,
                    qualityTier = qualityTier,
                    qualityScore = score
                ) to voice
            }
            .sortedByDescending { it.first.qualityScore }

        val voiceInfoList = ranked.map { it.first }
        voiceMap.clear()
        ranked.forEach { (info, voice) -> voiceMap[info.name] = voice }

        // Auto-select the best voice (highest composite score = female neural at device locale)
        val bestVoice = ranked.firstOrNull()
        if (bestVoice != null) {
            tts?.voice = bestVoice.second
            Log.i(TAG, "Auto-selected voice: ${bestVoice.first.displayName} (${bestVoice.first.name}, score=${bestVoice.first.qualityScore})")
        }

        _state.value = _state.value.copy(
            availableVoices = voiceInfoList,
            selectedVoiceName = bestVoice?.first?.name ?: "",
            selectedVoiceLabel = bestVoice?.first?.displayName ?: "Default"
        )
    }

    /**
     * Switch to a user-selected custom voice by name.
     * If currently speaking, restarts the current sentence with the new voice immediately.
     */
    fun selectVoice(voiceName: String) {
        val voice = voiceMap[voiceName] ?: return
        tts?.voice = voice
        val info = _state.value.availableVoices.find { it.name == voiceName }
        _state.value = _state.value.copy(
            selectedVoiceName = voiceName,
            selectedVoiceLabel = info?.displayName ?: voiceName
        )
        if (_state.value.isPlaying) {
            speakSentence(_state.value.currentSentenceIndex)
        }
        Log.i(TAG, "Voice switched to: ${info?.displayName ?: voiceName}")
    }

    fun setPitch(newPitch: Float) {
        val validPitch = newPitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(validPitch)
        _state.value = _state.value.copy(pitch = validPitch)
        if (_state.value.isPlaying) {
            speakSentence(_state.value.currentSentenceIndex)
        }
    }

    /**
     * Intelligent sentence segmentation for natural TTS prosody.
     * Protects abbreviations (Dr., Mr., etc.), decimal numbers (3.14),
     * splits on paragraph breaks and sentence-ending punctuation,
     * and breaks overly long sentences at clause boundaries.
     */
    private fun segmentSentencesNaturally(fullText: String): List<String> {
        val cleaned = fullText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\t", " ")

        // Protect abbreviations and decimals from false sentence splits
        val abbreviations = listOf(
            "Mr", "Mrs", "Ms", "Dr", "Prof", "Sr", "Jr", "St", "vs", "etc",
            "approx", "dept", "est", "govt", "inc", "corp", "ltd",
            "Vol", "Pg", "Fig", "Ref", "Ch", "Sec", "Ed", "Rev", "Gen"
        )
        var protectedText = cleaned
        for (abbr in abbreviations) {
            protectedText = protectedText.replace(
                Regex("(?i)\\b($abbr)\\."), "$1\u00B7"
            )
        }
        // Protect i.e. and e.g.
        protectedText = protectedText.replace(Regex("(?i)\\b(i\\.e|e\\.g|cf|al)\\."), "$1\u00B7")
        // Protect decimals (3.14) and version numbers (v2.0)
        protectedText = protectedText.replace(Regex("(\\d)\\.(\\d)"), "$1\u00B7$2")

        // Split on sentence-ending punctuation, paragraph breaks, or newline before uppercase
        val rawParts = protectedText
            .split(Regex("(?<=[.!?])\\s+|\\n{2,}|\\n(?=[A-Z])"))
            .map { it.replace("\u00B7", ".").replace("\n", " ").trim() }
            .filter { it.length > 2 }

        // Break overly long sentences at clause boundaries for natural breathing
        val result = mutableListOf<String>()
        for (sentence in rawParts) {
            if (sentence.length > 220) {
                val clauseParts = sentence.split(
                    Regex("(?<=[;:])\\s+|,\\s+(?=(?:and|but|or|so|yet|which|where|when|while|because|although|however|therefore|furthermore|moreover|nevertheless)\\b)", RegexOption.IGNORE_CASE)
                )
                var current = ""
                for (part in clauseParts) {
                    if (current.length + part.length > 200 && current.isNotEmpty()) {
                        result.add(current.trim())
                        current = part
                    } else {
                        current = if (current.isEmpty()) part else "$current $part"
                    }
                }
                if (current.isNotBlank()) result.add(current.trim())
            } else {
                result.add(sentence)
            }
        }

        return result.filter { it.length > 2 }.ifEmpty { listOf(fullText.trim()) }
    }

    private fun speakSentence(index: Int) {
        if (index in sentences.indices) {
            fallbackTimerJob?.cancel()
            hasReceivedRangeStart = false

            val sentence = sentences[index]
            val sentWords = sentenceWordMap.getOrElse(index) { emptyList() }

            _state.value = _state.value.copy(
                currentSentenceIndex = index,
                currentSentence = sentence,
                isPlaying = true,
                isPaused = false,
                activeSentenceWords = sentWords,
                activeWordIndex = if (sentWords.isNotEmpty()) 0 else -1,
                activeWord = sentWords.firstOrNull()
            )

            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sentence_$index")
            tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, params, "sentence_$index")
        }
    }

    /**
     * Maps character offsets from onRangeStart to the exact ExtractedWord being spoken.
     * The TTS engine gives us [start, end) offsets into the sentence string being synthesized.
     * We extract the spoken fragment and match it against our sentence words sequentially.
     */
    private fun highlightWordAtCharOffset(charStart: Int, charEnd: Int) {
        val sentIdx = _state.value.currentSentenceIndex
        val sentWords = sentenceWordMap.getOrElse(sentIdx) { emptyList() }
        if (sentWords.isEmpty()) return

        val sentence = sentences.getOrNull(sentIdx) ?: return
        val spokenText = if (charStart >= 0 && charEnd <= sentence.length && charStart < charEnd) {
            sentence.substring(charStart, charEnd).trim()
        } else null

        val currentIdx = _state.value.activeWordIndex.coerceAtLeast(0)
        var matchedIdx = currentIdx

        if (!spokenText.isNullOrBlank()) {
            val cleanSpoken = spokenText.trimEnd('.', ',', ';', ':', '!', '?', ')', '"', '\'')

            // Forward scan from current position — words come in order, baby
            for (i in currentIdx until sentWords.size) {
                val wt = sentWords[i].text.trim()
                val cleanWt = wt.trimEnd('.', ',', ';', ':', '!', '?', ')', '"', '\'')
                if (cleanWt.equals(cleanSpoken, ignoreCase = true) ||
                    wt.equals(spokenText, ignoreCase = true) ||
                    cleanWt.startsWith(cleanSpoken, ignoreCase = true) ||
                    cleanSpoken.startsWith(cleanWt, ignoreCase = true)) {
                    matchedIdx = i
                    break
                }
            }
        } else {
            // No text fragment — just advance sequentially
            matchedIdx = (currentIdx + 1).coerceAtMost(sentWords.size - 1)
        }

        _state.value = _state.value.copy(
            activeWordIndex = matchedIdx,
            activeWord = sentWords.getOrNull(matchedIdx)
        )
    }

    /**
     * Fallback word-by-word progression for TTS engines that don't fire onRangeStart.
     * Estimates timing from word length and speech rate. Self-cancels if the real
     * onRangeStart callback arrives (we always prefer ground truth).
     */
    private fun startFallbackWordTimer() {
        fallbackTimerJob?.cancel()
        val sentWords = _state.value.activeSentenceWords
        if (sentWords.isEmpty()) return

        fallbackTimerJob = scope.launch {
            // Grace period — give onRangeStart a chance to fire before we take over
            delay(150)
            if (hasReceivedRangeStart || !isActive) return@launch

            val rate = _state.value.speechRate.coerceAtLeast(0.5f)
            for (i in sentWords.indices) {
                if (!isActive || hasReceivedRangeStart) break
                _state.value = _state.value.copy(
                    activeWordIndex = i,
                    activeWord = sentWords[i]
                )
                // Estimated speaking time: ~55ms per char at 1.0x, floor 180ms, ceiling 1200ms
                val wordLen = sentWords[i].text.length
                val delayMs = ((wordLen * 55L + 180L) / rate).toLong().coerceIn(180L, 1200L)
                delay(delayMs)
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attr)
                .build()
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
