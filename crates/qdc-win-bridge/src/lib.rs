use std::ffi::{c_char, CStr, CString};
use std::sync::{Mutex, Once};
use std::path::{Path, PathBuf};
use log::{error, info};
use once_cell::sync::Lazy;

use pdf_kernel::engine::PdfEngine;
use speech_engine::{MockTtsEngine, TtsEngine};
use text_engine::extractor::TextExtractor;

static INIT: Once = Once::new();

// PDFium is !Send and !Sync, but we protect it with a Mutex.
struct SafeEngine(PdfEngine);
unsafe impl Send for SafeEngine {}
unsafe impl Sync for SafeEngine {}

// Global state for the Windows App
struct QdcState {
    kernel: Option<SafeEngine>,
    active_path: Option<PathBuf>,
    tts: MockTtsEngine,
}

static STATE: Lazy<Mutex<QdcState>> = Lazy::new(|| {
    Mutex::new(QdcState {
        kernel: None,
        active_path: None,
        tts: MockTtsEngine::new(),
    })
});

/// Initializes the QDC Engine. Must be called once at application startup.
#[unsafe(no_mangle)]
pub extern "C" fn qdc_init() -> i32 {
    INIT.call_once(|| {
        env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();
        info!("QDC Engine Initialized (WinUI 3 Bridge)");
    });
    
    // Initialize the kernel
    if let Ok(mut state) = STATE.lock() {
        let current_dir = std::env::current_dir().unwrap_or_else(|_| PathBuf::from("."));
        match PdfEngine::new(&current_dir) {
            Ok(kernel) => {
                state.kernel = Some(SafeEngine(kernel));
                0
            }
            Err(e) => {
                error!("Failed to initialize PdfEngine: {:?}", e);
                -1
            }
        }
    } else {
        -2
    }
}

/// Health check
#[unsafe(no_mangle)]
pub extern "C" fn qdc_health_check() -> *const c_char {
    b"QDC_OK\0".as_ptr() as *const c_char
}

/// Opens a PDF document and caches it in global state.
#[unsafe(no_mangle)]
pub extern "C" fn qdc_open_document(path: *const c_char) -> i32 {
    if path.is_null() { return -1; }
    let c_str = unsafe { CStr::from_ptr(path) };
    let file_path = match c_str.to_str() {
        Ok(s) => s,
        Err(_) => return -2,
    };

    info!("Opening PDF document: {}", file_path);

    if let Ok(mut state) = STATE.lock() {
        state.active_path = Some(PathBuf::from(file_path));
        if let Some(SafeEngine(kernel)) = &mut state.kernel {
            if let Err(e) = kernel.open_cached_doc(Path::new(file_path), None) {
                error!("Failed to load document: {:?}", e);
                return -3;
            }
        }
        0
    } else {
        error!("Failed to lock QDC State");
        -4
    }
}

/// Renders a page with high-quality SSAA and writes directly into the C# buffer.
#[unsafe(no_mangle)]
pub extern "C" fn qdc_render_page(pixel_buffer: *mut u8, width: u32, height: u32, page_index: u16) -> i32 {
    info!("qdc_render_page called: {}x{} page {}", width, height, page_index);
    if pixel_buffer.is_null() { return -1; }

    let mut state = match STATE.lock() {
        Ok(s) => s,
        Err(_) => return -2,
    };

    let path = match state.active_path.clone() {
        Some(p) => p,
        None => return -3,
    };

    let kernel = match &mut state.kernel {
        Some(SafeEngine(k)) => k,
        None => {
            error!("No document loaded");
            return -4;
        }
    };

    // Calculate DPI to fit width/height
    // For simplicity in the bridge, we'll just request a high DPI (e.g. 144)
    // The kernel will clamp to the max resolution.
    let dpi = 144.0; 
    
    match kernel.render_page_direct(&path, page_index as usize, dpi) {
        Ok(rgba_buffer) => {
            let length = (width * height * 4) as usize;
            let pixels = unsafe { std::slice::from_raw_parts_mut(pixel_buffer, length) };
            let raw_bytes = &rgba_buffer.data;
            
            // PDFium outputs BGRA, but our RgbaBuffer outputs RGBA! 
            // WriteableBitmap in C# is BGRA8.
            let copy_len = std::cmp::min(pixels.len(), raw_bytes.len());
            
            // Convert RGBA to BGRA
            for i in (0..copy_len).step_by(4) {
                if i + 3 < copy_len {
                    pixels[i] = raw_bytes[i + 2];     // B
                    pixels[i + 1] = raw_bytes[i + 1]; // G
                    pixels[i + 2] = raw_bytes[i];     // R
                    pixels[i + 3] = raw_bytes[i + 3]; // A
                }
            }
            
            info!("Successfully rendered page {} at {}x{}", page_index, width, height);
            0
        }
        Err(e) => {
            error!("Failed to render page: {:?}", e);
            -5
        }
    }
}

/// Converts the PDF to an actual Word Document (.docx)
#[unsafe(no_mangle)]
pub extern "C" fn qdc_convert_to_word(path: *const c_char) -> i32 {
    use docx_rs::*;
    
    if path.is_null() { return -1; }
    let c_str = unsafe { CStr::from_ptr(path) };
    let file_path = match c_str.to_str() {
        Ok(s) => s,
        Err(_) => return -2,
    };

    info!("Converting PDF to Word: {}", file_path);

    let mut state = match STATE.lock() {
        Ok(s) => s,
        Err(_) => return -3,
    };

    let kernel = match &mut state.kernel {
        Some(SafeEngine(k)) => k,
        None => return -4,
    };

    // Extract text from the PDF
    let mut _extractor = TextExtractor::new();
    let p = Path::new(file_path);
    let doc = match kernel.open_cached_doc(p, None) {
        Ok(d) => d,
        Err(_) => return -5,
    };
    
    let num_pages = doc.pages().len() as usize;
    let mut docx = Docx::new();
    
    for i in 0..num_pages {
        if let Ok(page) = doc.pages().get(i as u16) {
            if let Ok(text_page) = page.text() {
                let full_text = text_page.all();
                
                // Add text to the docx
                let para = Paragraph::new().add_run(Run::new().add_text(full_text));
                docx = docx.add_paragraph(para);
                
                if i < num_pages - 1 {
                    // Add page break
                    let break_para = Paragraph::new().add_run(Run::new().add_break(BreakType::Page));
                    docx = docx.add_paragraph(break_para);
                }
            }
        }
    }

    // Save as .docx
    let out_path = format!("{}.docx", file_path);
    let file = match std::fs::File::create(&out_path) {
        Ok(f) => f,
        Err(e) => {
            error!("Failed to create docx file: {:?}", e);
            return -5;
        }
    };
    
    if let Err(e) = docx.build().pack(file) {
        error!("Failed to pack docx: {:?}", e);
        return -6;
    }

    info!("Successfully converted to Word: {}", out_path);
    0
}

/// Request TTS generation for a string of text.
/// Returns a pointer to WAV byte data.
#[unsafe(no_mangle)]
pub extern "C" fn qdc_tts_synthesize(text: *const c_char, out_len: *mut u32) -> *mut u8 {
    if text.is_null() || out_len.is_null() { return std::ptr::null_mut(); }
    
    let c_str = unsafe { CStr::from_ptr(text) };
    let text_str = match c_str.to_str() {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    // Call the async TTS engine (blocking here for FFI simplicity)
    let rt = tokio::runtime::Runtime::new().unwrap();
    let result = rt.block_on(async {
        let state = STATE.lock().unwrap();
        state.tts.synthesize(text_str, "gemini-lady-voice").await
    });

    match result {
        Ok(bytes) => {
            unsafe { *out_len = bytes.len() as u32; }
            let mut vec = bytes.to_vec();
            vec.shrink_to_fit();
            let ptr = vec.as_mut_ptr();
            std::mem::forget(vec);
            ptr
        }
        Err(e) => {
            error!("TTS Error: {:?}", e);
            std::ptr::null_mut()
        }
    }
}

/// Free the allocated TTS byte buffer.
#[unsafe(no_mangle)]
pub extern "C" fn qdc_tts_free_buffer(buffer: *mut u8, len: u32) {
    if !buffer.is_null() {
        unsafe {
            drop(Vec::from_raw_parts(buffer, len as usize, len as usize));
        }
    }
}
