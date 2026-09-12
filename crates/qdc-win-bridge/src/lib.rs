use std::ffi::{c_char, CStr, CString};
use std::sync::{Mutex, Once};
use log::{error, info};
use once_cell::sync::Lazy;

use pdf_kernel::engine::{PdfKernel, PdfKernelConfig};
use pdfium_render::prelude::*;
use speech_engine::{MockTtsEngine, TtsEngine};
use text_engine::extractor::TextExtractor;

static INIT: Once = Once::new();

// Global state for the Windows App
struct QdcState {
    kernel: Option<PdfKernel>,
    tts: MockTtsEngine,
}

static STATE: Lazy<Mutex<QdcState>> = Lazy::new(|| {
    Mutex::new(QdcState {
        kernel: None,
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
    0
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

    let config = PdfKernelConfig {
        cache_memory_mb: 256,
        enable_gpu_acceleration: true,
        ..Default::default()
    };

    let mut kernel = PdfKernel::new(config);
    if let Err(e) = kernel.load_document(file_path) {
        error!("Failed to load document: {:?}", e);
        return -3;
    }

    if let Ok(mut state) = STATE.lock() {
        state.kernel = Some(kernel);
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

    let kernel = match &mut state.kernel {
        Some(k) => k,
        None => {
            error!("No document loaded");
            return -3;
        }
    };

    // Use PDFium's render config with SSAA and LCD text anti-aliasing
    let config = PdfRenderConfig::new()
        .set_target_width(width as u16)
        .set_maximum_height(height as u16)
        .clear_render_flags()
        .set_render_for_printing(false)
        .set_optimize_text_for_lcd(true); // Enhances text clarity on PC

    match kernel.render_page_custom(page_index, &config) {
        Ok(bitmap) => {
            let length = (width * height * 4) as usize;
            let pixels = unsafe { std::slice::from_raw_parts_mut(pixel_buffer, length) };
            let raw_bytes = bitmap.as_bytes();
            
            // PDFium outputs BGRA bytes, which perfectly matches WriteableBitmap!
            let copy_len = std::cmp::min(pixels.len(), raw_bytes.len());
            pixels[..copy_len].copy_from_slice(&raw_bytes[..copy_len]);
            
            info!("Successfully rendered page {} at {}x{}", page_index, width, height);
            0
        }
        Err(e) => {
            error!("Failed to render page: {:?}", e);
            -4
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
        Some(k) => k,
        None => return -4,
    };

    // Extract text from the PDF
    let mut extractor = TextExtractor::new();
    let num_pages = kernel.page_count();
    
    let mut doc = Docx::new();
    
    for i in 0..num_pages {
        if let Ok(page) = kernel.get_page(i) {
            let extracted = extractor.extract_page_text(&page, i);
            
            // Add text to the docx
            let para = Paragraph::new().add_run(Run::new().add_text(extracted.raw_text));
            doc = doc.add_paragraph(para);
            
            if i < num_pages - 1 {
                // Add page break
                let break_para = Paragraph::new().add_run(Run::new().add_break(BreakType::Page));
                doc = doc.add_paragraph(break_para);
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
    
    if let Err(e) = doc.build().pack(file) {
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
