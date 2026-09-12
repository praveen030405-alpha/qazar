//! Windows C-ABI Bridge for the QDC Core Engine
//!
//! This crate exposes the pure Rust backend as a dynamic library (`qdc_engine.dll`)
//! consumable via P/Invoke from a C# WinUI 3 frontend application.

use std::ffi::{c_char, CStr, CString};
use std::sync::Once;
use log::{info, error};

static INIT: Once = Once::new();

/// Initializes the QDC Engine. Must be called once at application startup.
/// Returns 0 on success, -1 on failure.
#[unsafe(no_mangle)]
pub extern "C" fn qdc_init() -> i32 {
    INIT.call_once(|| {
        // Initialize logger, default to info
        env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();
        info!("QDC Engine Initialized (WinUI 3 Bridge)");
    });
    0
}

/// Simple health check function
/// Returns a pointer to a C-string "QDC_OK" which must NOT be freed by the caller.
#[unsafe(no_mangle)]
pub extern "C" fn qdc_health_check() -> *const c_char {
    b"QDC_OK\0".as_ptr() as *const c_char
}

/// Renders a dummy PDF page (gradient) into a memory buffer passed from C#.
#[unsafe(no_mangle)]
pub extern "C" fn qdc_render_page(pixel_buffer: *mut u8, width: u32, height: u32) -> i32 {
    info!("qdc_render_page called with buffer: {:p}, width: {}, height: {}", pixel_buffer, width, height);

    if pixel_buffer.is_null() {
        error!("Pixel buffer is null!");
        return -1;
    }

    let length = (width * height * 4) as usize;
    let pixels = unsafe { std::slice::from_raw_parts_mut(pixel_buffer, length) };

    // Fill with a nice purple/blue gradient to prove rendering works!
    for y in 0..height {
        for x in 0..width {
            let offset = ((y * width + x) * 4) as usize;
            
            // BGRA format for WriteableBitmap (or RGBA depending on stream)
            // C# AsStream() is typically BGRA or RGBA. Let's do a safe gradient.
            let r = ((x as f32 / width as f32) * 255.0) as u8;
            let g = ((y as f32 / height as f32) * 255.0) as u8;
            let b = 150;
            let a = 255;
            
            // WriteableBitmap stream expects BGRA
            pixels[offset] = b;     // B
            pixels[offset + 1] = g; // G
            pixels[offset + 2] = r; // R
            pixels[offset + 3] = a; // A
        }
    }

    info!("Rendered {}x{} gradient successfully.", width, height);
    0
}

// TODO: 
// 1. Opaque handle management for DocumentHandle
// 2. wgpu DirectX 12 SwapChain sharing for gpu-compositor

/// Simulates converting a PDF to a Word document via the Rust Engine.
#[unsafe(no_mangle)]
pub extern "C" fn qdc_convert_to_word(path: *const c_char) -> i32 {
    if path.is_null() {
        return -1;
    }
    
    let c_str = unsafe { std::ffi::CStr::from_ptr(path) };
    let file_path = match c_str.to_str() {
        Ok(s) => s,
        Err(_) => return -2,
    };

    info!("QDC Toolkit: Received request to convert '{}' to Word", file_path);

    // Simulate heavy Rust computation (e.g., pdf-kernel -> OOXML generation)
    std::thread::sleep(std::time::Duration::from_secs(3));
    
    info!("QDC Toolkit: Conversion of '{}' completed successfully.", file_path);
    
    0 // Success
}
