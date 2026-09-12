use async_trait::async_trait;
use bytes::Bytes;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum SpeechError {
    #[error("API key is missing")]
    MissingApiKey,
    #[error("Network error: {0}")]
    NetworkError(String),
    #[error("Invalid voice specified")]
    InvalidVoice,
    #[error("Failed to generate speech: {0}")]
    GenerationError(String),
}

/// The core interface for a Text-to-Speech engine.
#[async_trait]
pub trait TtsEngine: Send + Sync {
    /// Initializes the engine with an API key, if required.
    fn set_api_key(&mut self, key: String);

    /// Synthesizes the given text into an audio byte stream (e.g., MP3 or WAV).
    async fn synthesize(&self, text: &str, voice: &str) -> Result<Bytes, SpeechError>;
}

/// A mocked TTS engine for development or when an API key is not available.
pub struct MockTtsEngine {
    api_key: Option<String>,
}

impl MockTtsEngine {
    pub fn new() -> Self {
        Self { api_key: None }
    }
}

#[async_trait]
impl TtsEngine for MockTtsEngine {
    fn set_api_key(&mut self, key: String) {
        self.api_key = Some(key);
    }

    async fn synthesize(&self, text: &str, _voice: &str) -> Result<Bytes, SpeechError> {
        if self.api_key.is_none() {
            log::warn!("MockTtsEngine: Synthesizing without an API key. (Mocking audio...)");
        } else {
            log::info!("MockTtsEngine: Synthesizing text using API key...");
        }
        
        // In a real implementation, this would make an HTTP request to Google Cloud or ElevenLabs.
        // For the mock, we just return a tiny valid WAV file header with silence, or just an empty byte array.
        // The frontend will play this back.
        log::debug!("Synthesizing: '{}'", text);
        
        // Return 1 second of silent WAV data as a mock.
        // This is a minimal valid WAV header.
        let wav_header: [u8; 44] = [
            0x52, 0x49, 0x46, 0x46, // "RIFF"
            0x24, 0x00, 0x00, 0x00, // Chunk size
            0x57, 0x41, 0x56, 0x45, // "WAVE"
            0x66, 0x6d, 0x74, 0x20, // "fmt "
            0x10, 0x00, 0x00, 0x00, // Subchunk1Size (16 for PCM)
            0x01, 0x00,             // AudioFormat (1 for PCM)
            0x01, 0x00,             // NumChannels (1)
            0x44, 0xac, 0x00, 0x00, // SampleRate (44100)
            0x88, 0x58, 0x01, 0x00, // ByteRate (44100 * 1 * 2)
            0x02, 0x00,             // BlockAlign (1 * 2)
            0x10, 0x00,             // BitsPerSample (16)
            0x64, 0x61, 0x74, 0x61, // "data"
            0x00, 0x00, 0x00, 0x00, // Subchunk2Size (0 bytes of data for silence)
        ];
        
        Ok(Bytes::from(wav_header.to_vec()))
    }
}
