//! # Continuous Fuzzing Engine for PDF Parser Boundary (Section 6)
//!
//! Continuous, evolutionary mutation-based fuzzing harness targeting:
//! - PDF parser boundary (header, xref table, indirect objects, dictionary streams)
//! - Content-stream interpreter and font decoding stack
//! - JBIG2 and JPEG2000 image filter boundaries
//! - Malformed dictionary structures and circular references
//! - Risky content policies (JavaScript, Launch, external URI actions)

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use anyhow::{Context, Result};
use pdf_kernel::PdfEngine;
use serde::{Deserialize, Serialize};

/// Fast, deterministic PRNG with zero external dependencies
struct XorShift64 {
    state: u64,
}

impl XorShift64 {
    fn new(seed: u64) -> Self {
        Self {
            state: if seed == 0 { 0x853c49e6748fea9b } else { seed },
        }
    }

    fn next_u64(&mut self) -> u64 {
        let mut x = self.state;
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        self.state = x;
        x
    }

    fn next_usize(&mut self, bound: usize) -> usize {
        if bound == 0 {
            0
        } else {
            (self.next_u64() as usize) % bound
        }
    }

    fn next_u8(&mut self) -> u8 {
        self.next_u64() as u8
    }

    fn next_bool(&mut self) -> bool {
        (self.next_u64() & 1) == 1
    }
}

/// Real-time fuzzing telemetry written to disk for external observers
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FuzzTelemetry {
    pub total_fuzzed: u64,
    pub inputs_per_sec: f64,
    pub crashes_detected: u64,
    pub rejections_caught: u64,
    pub valid_parsed: u64,
    pub uptime_seconds: f64,
    pub seed_count: usize,
    pub status: String,
    pub last_mutation: String,
}

/// Evolutionary Mutator for PDF binary structures
struct PdfMutator {
    prng: XorShift64,
    seeds: Vec<Vec<u8>>,
    hostile_tokens: Vec<&'static [u8]>,
}

impl PdfMutator {
    fn new(seeds: Vec<Vec<u8>>) -> Self {
        let hostile_tokens: Vec<&'static [u8]> = vec![
            b"/JavaScript (app.alert('PWNED'))",
            b"/Launch /F (cmd.exe) /P (/c calc.exe)",
            b"/URI (http://malicious-host.internal/leak)",
            b"/Filter /JBIG2Decode",
            b"/Filter /JPXDecode",
            b"/Filter [/ASCII85Decode /FlateDecode /JBIG2Decode]",
            b"/XRef /Size 2147483647",
            b"/XRef /Size -1",
            b"/Length 2147483647",
            b"/Length -99999",
            b"/Subtype /Type3 /CharProcs << /A 10 0 R >>",
            b"/Kids [10 0 R 10 0 R 10 0 R 10 0 R]",
            b"9999999999 00000 n \r\n",
            b"0000000000 65535 f \r\n",
            b"\x00\x00\x00\x00\x00\x00\x00\x00",
            b"trailer << /Root 1 0 R /Size 0 >> startxref 0 %%EOF",
            b"%%EOF\n%%EOF\n%%EOF\n%%EOF",
            b"/Encrypt << /Filter /Standard /V 5 /R 6 /P -1 >>",
        ];

        Self {
            prng: XorShift64::new(0xdeadbeef12345678),
            seeds,
            hostile_tokens,
        }
    }

    fn mutate(&mut self) -> (Vec<u8>, &'static str) {
        if self.seeds.is_empty() {
            return (b"%PDF-1.4\n%%EOF".to_vec(), "fallback");
        }

        let seed_idx = self.prng.next_usize(self.seeds.len());
        let mut buf = self.seeds[seed_idx].clone();
        let strategy = self.prng.next_usize(7);

        let mutation_name = match strategy {
            0 => {
                // 1. Bit flip (1, 2, or 4 random bits)
                let num_flips = 1 + self.prng.next_usize(4);
                for _ in 0..num_flips {
                    if !buf.is_empty() {
                        let pos = self.prng.next_usize(buf.len());
                        let bit = self.prng.next_usize(8);
                        buf[pos] ^= 1 << bit;
                    }
                }
                "bit_flip"
            }
            1 => {
                // 2. Random byte substitutions
                let count = 1 + self.prng.next_usize(8);
                for _ in 0..count {
                    if !buf.is_empty() {
                        let pos = self.prng.next_usize(buf.len());
                        buf[pos] = self.prng.next_u8();
                    }
                }
                "byte_substitute"
            }
            2 => {
                // 3. Hostile token dictionary injection
                let token = self.hostile_tokens[self.prng.next_usize(self.hostile_tokens.len())];
                if buf.len() > 10 {
                    let insert_pos = 9 + self.prng.next_usize(buf.len() - 9);
                    let mut new_buf = Vec::with_capacity(buf.len() + token.len());
                    new_buf.extend_from_slice(&buf[..insert_pos]);
                    new_buf.extend_from_slice(token);
                    new_buf.extend_from_slice(&buf[insert_pos..]);
                    buf = new_buf;
                }
                "hostile_token_inject"
            }
            3 => {
                // 4. Stream truncation / payload splicing
                if buf.len() > 32 {
                    let cut_point = 16 + self.prng.next_usize(buf.len() - 16);
                    buf.truncate(cut_point);
                    if self.prng.next_bool() {
                        buf.extend_from_slice(b"\n%%EOF");
                    }
                }
                "stream_truncate"
            }
            4 => {
                // 5. Byte deletion / block erasure
                if buf.len() > 64 {
                    let erase_len = 1 + self.prng.next_usize(32);
                    let pos = self.prng.next_usize(buf.len() - erase_len);
                    buf.drain(pos..(pos + erase_len));
                }
                "block_erasure"
            }
            5 => {
                // 6. Crossover splicing from another seed
                if self.seeds.len() > 1 {
                    let other_idx = (seed_idx + 1) % self.seeds.len();
                    let other = &self.seeds[other_idx];
                    if !other.is_empty() && buf.len() > 10 {
                        let slice_start = self.prng.next_usize(other.len());
                        let slice_len = self.prng.next_usize(other.len() - slice_start).min(128);
                        let splice_data = &other[slice_start..(slice_start + slice_len)];
                        let insert_pos = self.prng.next_usize(buf.len());
                        buf.splice(insert_pos..insert_pos, splice_data.iter().cloned());
                    }
                }
                "crossover_splice"
            }
            _ => {
                // 7. Null-byte / extreme boundary flood
                if !buf.is_empty() {
                    let flood_len = 8 + self.prng.next_usize(64);
                    let pos = self.prng.next_usize(buf.len());
                    let val = if self.prng.next_bool() { 0x00 } else { 0xFF };
                    let fill = vec![val; flood_len];
                    buf.splice(pos..pos, fill);
                }
                "null_boundary_flood"
            }
        };

        (buf, mutation_name)
    }
}

fn main() -> Result<()> {
    env_logger::init();

    println!("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    println!("  MERIDIAN CONTINUOUS FUZZING HARNESS — PARSER BOUNDARY (SEC. 6)");
    println!("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

    let running = Arc::new(AtomicBool::new(true));
    let r = Arc::clone(&running);
    ctrlc_handler(r);

    // Locate PDFium library
    let exe_dir = std::env::current_exe().ok().and_then(|p| p.parent().map(|d| d.to_path_buf()));
    let lib_dir = find_pdfium_dir(&exe_dir)?;
    println!("  [1] Initializing PDFium Engine at: {}", lib_dir.display());
    let engine = PdfEngine::new(&lib_dir).context("Failed to initialize PDFium engine")?;

    // Load Initial Seed Corpora
    println!("  [2] Ingesting Seed Corpus:");
    let mut seeds = Vec::new();
    let seed_paths = [
        "test-corpus/adversarial_malformed.pdf",
        "test-corpus/qoi-specification.pdf",
        "test-corpus/mixed_pages.pdf",
        "test-corpus/letter_portrait.pdf",
    ];

    for path in &seed_paths {
        if let Ok(bytes) = fs::read(path) {
            println!("      Loaded seed: {:<35} ({} bytes)", path, bytes.len());
            seeds.push(bytes);
        }
    }

    // Add minimal synthesized PDF templates
    seeds.push(generate_minimal_pdf());
    seeds.push(generate_xref_stream_pdf());
    seeds.push(generate_jbig2_filter_stub_pdf());
    println!("      Synthesized templates: 3 in-memory seed generators added");
    println!("      Total Active Seeds   : {}", seeds.len());
    println!();

    // Setup output directories
    let crash_dir = PathBuf::from("artifacts/fuzz/crashes");
    fs::create_dir_all(&crash_dir)?;
    let telemetry_path = PathBuf::from("artifacts/fuzz/telemetry.json");

    let mut mutator = PdfMutator::new(seeds);
    let start_time = Instant::now();
    let mut total_fuzzed = 0u64;
    let mut crashes_detected = 0u64;
    let mut rejections_caught = 0u64;
    let mut valid_parsed = 0u64;

    let mut last_heartbeat = Instant::now();
    let mut last_telemetry_save = Instant::now();

    println!("  [3] Starting Continuous Evolutionary Fuzzing Loop...");
    println!("      Boundary Targets : Header, XRef, Content Streams, JBIG2/JPX, Font CMap");
    println!("      Telemetry Output : {}", telemetry_path.display());
    println!("      Crash Directory  : {}", crash_dir.display());
    println!();

    while running.load(Ordering::Relaxed) {
        let (mutated_bytes, mutation_name) = mutator.mutate();
        total_fuzzed += 1;

        // Execute against parser boundary inside panic boundary guard
        let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            match engine.open_bytes(&mutated_bytes, None) {
                Ok(doc) => {
                    // Document parsed structure; stress second-stage interpreter boundaries
                    let _info = doc.info();
                    let page_count = doc.page_count();
                    if page_count > 0 {
                        // Exercise MediaBox, rasterizer, and font glyph decoder on page 0
                        let _ = doc.page_dimensions(0);
                        let _ = doc.render_page(0, 36.0); // low dpi fast raster
                        let _ = doc.extract_page_text(0);
                    }
                    Ok(())
                }
                Err(e) => Err(e),
            }
        }));

        match result {
            Ok(inner_res) => match inner_res {
                Ok(_) => {
                    valid_parsed += 1;
                }
                Err(_expected_err) => {
                    // Graceful rejection (corrupt stream, invalid xref, syntax error, etc.)
                    rejections_caught += 1;
                }
            },
            Err(panic_payload) => {
                // Genuine Crash / Panic detected!
                crashes_detected += 1;
                let crash_id = format!("crash_{}_{}", total_fuzzed, mutation_name);
                let crash_file = crash_dir.join(format!("{}.pdf", crash_id));
                let _ = fs::write(&crash_file, &mutated_bytes);

                let reason = if let Some(s) = panic_payload.downcast_ref::<&str>() {
                    s.to_string()
                } else if let Some(s) = panic_payload.downcast_ref::<String>() {
                    s.clone()
                } else {
                    "Unknown panic".to_string()
                };

                eprintln!("  🚨 CRASH DETECTED on input #{}: {} -> Saved to {}", total_fuzzed, reason, crash_file.display());
            }
        }

        // Heartbeat log every 2 seconds or 5,000 iterations
        if last_heartbeat.elapsed() >= Duration::from_secs(2) || (total_fuzzed % 5000 == 0) {
            let elapsed_sec = start_time.elapsed().as_secs_f64();
            let ips = total_fuzzed as f64 / elapsed_sec.max(0.001);

            println!("  [Fuzz Mileage] {:>8} inputs | {:>6.1} exec/s | Rejections: {:>8} | Parsed: {:>5} | Crashes: {} | Uptime: {:.1}s",
                total_fuzzed, ips, rejections_caught, valid_parsed, crashes_detected, elapsed_sec);

            last_heartbeat = Instant::now();
        }

        // Periodic atomic telemetry flush
        if last_telemetry_save.elapsed() >= Duration::from_millis(500) {
            let elapsed_sec = start_time.elapsed().as_secs_f64();
            let ips = total_fuzzed as f64 / elapsed_sec.max(0.001);

            let telem = FuzzTelemetry {
                total_fuzzed,
                inputs_per_sec: ips,
                crashes_detected,
                rejections_caught,
                valid_parsed,
                uptime_seconds: elapsed_sec,
                seed_count: mutator.seeds.len(),
                status: if crashes_detected == 0 { "RUNNING_CLEAN".to_string() } else { "CRASHES_REPORTED".to_string() },
                last_mutation: mutation_name.to_string(),
            };

            let _ = save_telemetry_atomic(&telemetry_path, &telem);
            let _ = save_telemetry_atomic(&PathBuf::from("target/fuzz_telemetry.json"), &telem);
            last_telemetry_save = Instant::now();
        }
    }

    let final_elapsed = start_time.elapsed().as_secs_f64();
    println!();
    println!("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    println!("  FUZZING HARNESS SESSION SUMMARY");
    println!("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    println!("  Total Executions      : {}", total_fuzzed);
    println!("  Elapsed Uptime        : {:.2}s", final_elapsed);
    println!("  Average Speed         : {:.1} inputs/sec", total_fuzzed as f64 / final_elapsed.max(0.001));
    println!("  Graceful Rejections   : {} (Safe defense against malformed inputs)", rejections_caught);
    println!("  Valid Parsed          : {}", valid_parsed);
    println!("  Crashes Detected      : {} (Target: 0)", crashes_detected);
    println!("  Final Status          : {}", if crashes_detected == 0 { "100% CLEAN — NO CRASHES" } else { "INVESTIGATION REQUIRED" });
    println!("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

    Ok(())
}

fn save_telemetry_atomic(path: &Path, telem: &FuzzTelemetry) -> Result<()> {
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    let temp_path = path.with_extension("tmp");
    let json = serde_json::to_string_pretty(telem)?;
    fs::write(&temp_path, json)?;
    fs::rename(&temp_path, path)?;
    Ok(())
}

fn ctrlc_handler(running: Arc<AtomicBool>) {
    let _ = ctrlc_native(move || {
        println!("\n  [!] Graceful shutdown requested... Flushing telemetry to disk.");
        running.store(false, Ordering::Relaxed);
    });
}

fn ctrlc_native<F: FnOnce() + Send + 'static>(handler: F) -> Result<()> {
    // Standard Windows Console Ctrl Handler
    #[cfg(target_os = "windows")]
    {
        use std::sync::Mutex;
        static HANDLER: Mutex<Option<Box<dyn FnOnce() + Send>>> = Mutex::new(None);
        *HANDLER.lock().unwrap() = Some(Box::new(handler));

        unsafe extern "system" fn win_handler(_ctrl_type: u32) -> i32 {
            if let Some(h) = HANDLER.lock().unwrap().take() {
                h();
            }
            1
        }

        unsafe extern "system" {
            fn SetConsoleCtrlHandler(handler: Option<unsafe extern "system" fn(u32) -> i32>, add: i32) -> i32;
        }
        unsafe {
            SetConsoleCtrlHandler(Some(win_handler), 1);
        }
    }
    Ok(())
}

fn find_pdfium_dir(exe_dir: &Option<PathBuf>) -> Result<PathBuf> {
    let project_bin = PathBuf::from("bin");
    if project_bin.join("pdfium.dll").exists() {
        return Ok(project_bin.canonicalize()?);
    }
    if let Some(dir) = exe_dir {
        if dir.join("pdfium.dll").exists() {
            return Ok(dir.clone());
        }
    }
    let cwd = std::env::current_dir()?;
    if cwd.join("pdfium.dll").exists() {
        return Ok(cwd);
    }
    anyhow::bail!("Could not find pdfium.dll");
}

fn generate_minimal_pdf() -> Vec<u8> {
    b"%PDF-1.4\n\
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n\
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n\
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >> endobj\n\
4 0 obj << /Length 21 >> stream\n\
BT /F1 12 Tf ET\n\
endstream endobj\n\
xref\n0 5\n\
0000000000 65535 f \n\
0000000009 00000 n \n\
0000000058 00000 n \n\
0000000115 00000 n \n\
0000000214 00000 n \n\
trailer << /Size 5 /Root 1 0 R >>\n\
startxref\n285\n%%EOF".to_vec()
}

fn generate_xref_stream_pdf() -> Vec<u8> {
    b"%PDF-1.5\n\
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n\
2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj\n\
3 0 obj << /Type /XRef /Size 4 /W [1 2 1] /Root 1 0 R /Length 16 >> stream\n\
\x00\x00\x00\x00\x01\x00\x09\x00\x01\x00\x3A\x00\x01\x00\x69\x00\n\
endstream endobj\n\
startxref\n105\n%%EOF".to_vec()
}

fn generate_jbig2_filter_stub_pdf() -> Vec<u8> {
    b"%PDF-1.4\n\
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n\
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n\
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << /XObject << /Im1 4 0 R >> >> >> endobj\n\
4 0 obj << /Type /XObject /Subtype /Image /Width 8 /Height 8 /ColorSpace /DeviceGray /BitsPerComponent 1 /Filter /JBIG2Decode /Length 12 >> stream\n\
\x97\x4A\x42\x32\x0D\x0A\x1A\x0A\x01\x00\x00\x00\n\
endstream endobj\n\
xref\n0 5\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000227 00000 n \n\
trailer << /Size 5 /Root 1 0 R >>\nstartxref\n415\n%%EOF".to_vec()
}
