//! Meridian Zero-Copy AHardwareBuffer Engine
//!
//! Provides direct native NDK AHardwareBuffer allocation and rasterization.
//! Renders PDFium output directly into GPU-accessible graphic memory with zero
//! CPU-to-GPU copies, zero JNI byte array allocations, and zero Skia heap overhead.

use std::path::Path;
use jni::JNIEnv;
use jni::sys::jobject;
use pdf_kernel::PdfEngine;
#[cfg(target_os = "android")]
use color_engine::{AdaptiveModeEngine, ViewingMode as CoreViewingMode};

#[cfg(target_os = "android")]
mod ndk {
    use std::os::raw::c_void;

    #[repr(C)]
    pub struct AHardwareBuffer {
        _unused: [u8; 0],
    }

    #[repr(C)]
    #[derive(Debug, Clone, Copy)]
    pub struct AHardwareBuffer_Desc {
        pub width: u32,
        pub height: u32,
        pub layers: u32,
        pub format: u32,
        pub usage: u64,
        pub stride: u32,
        pub rfu0: u32,
        pub rfu1: u64,
    }

    #[repr(C)]
    #[derive(Debug, Clone, Copy)]
    pub struct ARect {
        pub left: i32,
        pub top: i32,
        pub right: i32,
        pub bottom: i32,
    }

    pub const AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM: u32 = 1;
    pub const AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN: u64 = 3 << 4;
    pub const AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE: u64 = 1 << 8;

    #[link(name = "android")]
    unsafe extern "C" {
        pub fn AHardwareBuffer_allocate(
            desc: *const AHardwareBuffer_Desc,
            outBuffer: *mut *mut AHardwareBuffer,
        ) -> i32;

        pub fn AHardwareBuffer_describe(
            buffer: *const AHardwareBuffer,
            outDesc: *mut AHardwareBuffer_Desc,
        );

        pub fn AHardwareBuffer_lock(
            buffer: *mut AHardwareBuffer,
            usage: u64,
            fence: i32,
            rect: *const ARect,
            outVirtualAddress: *mut *mut c_void,
        ) -> i32;

        pub fn AHardwareBuffer_unlock(
            buffer: *mut AHardwareBuffer,
            fence: *mut i32,
        ) -> i32;

        pub fn AHardwareBuffer_release(buffer: *mut AHardwareBuffer);

        pub fn AHardwareBuffer_toHardwareBuffer(
            env: *mut jni::sys::JNIEnv,
            hardwareBuffer: *mut AHardwareBuffer,
        ) -> jni::sys::jobject;
    }
}

/// Renders a PDF page directly into an Android AHardwareBuffer.
/// Returns a Java `android.hardware.HardwareBuffer` object, or null on failure.
#[cfg(target_os = "android")]
pub fn render_to_hardware_buffer(
    engine: &mut PdfEngine,
    doc_path: &Path,
    page_index: usize,
    dpi: f32,
    viewing_mode: i32,
    env: &mut JNIEnv,
) -> jobject {
    use ndk::*;

    // 1. Rasterize page using PDFium engine
    let buffer = match engine.render_page_direct(doc_path, page_index, dpi) {
        Ok(buf) => buf,
        Err(e) => {
            log::debug!("render_to_hardware_buffer for page {}: {}", page_index, e);
            return std::ptr::null_mut();
        }
    };

    let width = buffer.width as u32;
    let height = buffer.height as u32;

    if width == 0 || height == 0 || width > 4096 || height > 4096 {
        return std::ptr::null_mut();
    }

    // 2. Allocate AHardwareBuffer with CPU_WRITE + GPU_SAMPLED usage flags
    let desc = AHardwareBuffer_Desc {
        width,
        height,
        layers: 1,
        format: AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
        usage: AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE,
        stride: 0,
        rfu0: 0,
        rfu1: 0,
    };

    let mut raw_ahb: *mut AHardwareBuffer = std::ptr::null_mut();
    let alloc_res = unsafe { AHardwareBuffer_allocate(&desc, &mut raw_ahb) };
    if alloc_res != 0 || raw_ahb.is_null() {
        log::warn!("AHardwareBuffer_allocate failed with code {}", alloc_res);
        return std::ptr::null_mut();
    }

    // 3. Query actual buffer stride assigned by graphics driver
    let mut actual_desc = desc;
    unsafe {
        AHardwareBuffer_describe(raw_ahb, &mut actual_desc);
    }
    let stride = actual_desc.stride.max(width);

    // 4. Lock buffer memory address for direct CPU writing
    let mut virtual_addr: *mut std::os::raw::c_void = std::ptr::null_mut();
    let lock_res = unsafe {
        AHardwareBuffer_lock(
            raw_ahb,
            AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
            -1,
            std::ptr::null(),
            &mut virtual_addr,
        )
    };

    if lock_res != 0 || virtual_addr.is_null() {
        log::error!("AHardwareBuffer_lock failed with code {}", lock_res);
        unsafe { AHardwareBuffer_release(raw_ahb) };
        return std::ptr::null_mut();
    }

    // 5. Stride-aware row copy directly into GPU-accessible mapped memory
    let dst_ptr = virtual_addr as *mut u8;
    let mut raw_pixels = buffer.data;

    // Apply linear-light chromatic adaptation if reading mode requested
    let core_mode = match viewing_mode {
        1 => CoreViewingMode::Paper,
        2 => CoreViewingMode::Sepia,
        _ => CoreViewingMode::Default,
    };
    if core_mode != CoreViewingMode::Default {
        AdaptiveModeEngine::adapt_rgba_buffer(&mut raw_pixels, core_mode);
    }

    let src_bytes = raw_pixels.as_slice();
    let row_bytes = (width * 4) as usize;
    let stride_bytes = (stride * 4) as usize;

    if stride == width {
        // Fast path: stride equals width, copy whole block in 1 operation
        let total_bytes = row_bytes * height as usize;
        unsafe {
            std::ptr::copy_nonoverlapping(src_bytes.as_ptr(), dst_ptr, total_bytes);
        }
    } else {
        // Driver stride padding present: copy row by row
        for y in 0..height as usize {
            let src_offset = y * row_bytes;
            let dst_offset = y * stride_bytes;
            unsafe {
                std::ptr::copy_nonoverlapping(
                    src_bytes.as_ptr().add(src_offset),
                    dst_ptr.add(dst_offset),
                    row_bytes,
                );
            }
        }
    }

    // 6. Unlock AHardwareBuffer and flush caches to GPU
    let mut fence: i32 = -1;
    unsafe {
        AHardwareBuffer_unlock(raw_ahb, &mut fence);
    }

    // 7. Convert native AHardwareBuffer* to Java android.hardware.HardwareBuffer
    let jni_raw_env = env.get_raw();
    let j_hw_buffer = unsafe { AHardwareBuffer_toHardwareBuffer(jni_raw_env, raw_ahb) };

    // Release native allocation reference; Java HardwareBuffer retains its own ref
    unsafe {
        AHardwareBuffer_release(raw_ahb);
    }

    log::info!("Zero-Copy AHardwareBuffer created: page={}, {}x{} (stride={}) direct GPU mapping", page_index, width, height, stride);

    j_hw_buffer
}

/// Fallback stub for non-Android targets (allows `cargo check --workspace` to pass on desktop)
#[cfg(not(target_os = "android"))]
pub fn render_to_hardware_buffer(
    _engine: &mut PdfEngine,
    _doc_path: &Path,
    _page_index: usize,
    _dpi: f32,
    _viewing_mode: i32,
    _env: &mut JNIEnv,
) -> jobject {
    std::ptr::null_mut()
}
