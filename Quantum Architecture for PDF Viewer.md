
# Meridian: Architecture Proposal for a Next-Generation PDF Viewer

**Thesis.** The incumbents are each best-in-class in exactly one dimension: Adobe Acrobat in rendering correctness, Apple Preview/Books in display pipeline polish, PDF Expert in scroll feel, Foxit in raw open speed, Edge in accessibility reach. None of them is built on a modern foundation: GPU-compute rasterization, a memory-governed tile scheduler, an operation-log annotation model, and on-device layout intelligence. Meridian is a from-scratch design that treats those four as the foundation, ships the table stakes flawlessly, and differentiates on things no incumbent attempts: color-managed ICC fidelity & adaptive paper/sepia modes, vector-crisp zoom at 120 Hz, cross-document continuous reading, and on-device reflow that respects layout intent.

---

## 1. Architecture principles

Non-negotiable constraints. Everything else in this document is derived from these.

| # | Principle | Concrete commitment |
|---|-----------|---------------------|
| P1 | Native core, no web tech | Rust core; zero WebView/Electron/JS in the document path. UI chrome is SwiftUI (Apple) / Jetpack Compose (Android). |
| P2 | GPU-first rendering | All page rasterization and compositing on GPU. CPU raster exists only as a correctness fallback and for CI golden images. |
| P3 | Offline-first | Every feature in Sections 3–5 works with radios off. Network is used only for opt-in sync and optional model downloads. |
| P4 | Mobile constraints are inputs, not bugs | Memory ceiling, thermal state, and battery are explicit signals consumed by the scheduler (Section 5). Design target is a 4 GB RAM mid-tier ARM64 device, not a flagship. |
| P5 | Every file is hostile | Parser runs isolated, fuzzed continuously, no embedded script execution ever (Section 6). |
| P6 | Deterministic core, optional intelligence | AI features (semantic search, summarization) are a removable layer. The engine below them is fully deterministic and testable. |

**Performance budgets** (targets, each with stated measurement methodology — these are commitments to be validated in Phase 0–1, not marketing numbers):

| Budget | Target | Methodology |
|--------|--------|-------------|
| Cold open → first page pixels | ≤ 300 ms | Median of 100 cold runs, OS page cache dropped, 50 MB scanned PDF (JPEG2000-heavy), Snapdragon 778G-class / A15 device, measured process-spawn → compositor present via platform profiler (Perfetto / Instruments signposts) |
| Cold-open budget breakdown | spawn 40 ms · xref/header parse 30 ms · page-1 objects 20 ms · font+image decode 80 ms · raster 60 ms · present 20 ms · slack 50 ms | Same harness, per-stage signposts; any stage exceeding budget blocks release |
| Scroll frame budget | 8.3 ms (120 Hz), ≤ 1% dropped frames during 2,000 px/s fling | Perfetto/Instruments trace over 60 s fling script, 500-page mixed text+image document |
| Page-turn commit | ≤ 4 ms, zero rasterization on the critical path | Transition must be a pure GPU transform of resident tiles |
| Zoom re-render | Vector re-rasterization p95 ≤ 16 ms at 4× zoom; mip fallback visible ≤ 2 frames | Scripted pinch-zoom trace; frame-diff analysis for upscale dwell |
| Memory ceiling | ≤ 250 MB resident per open document on 4 GB device (excludes OS page cache) | Central allocation accounting (Section 5); soak test with OS memory-pressure callbacks forced |
| Search latency | First result ≤ 100 ms on a pre-indexed 10,000-page library | Median of 1,000 queries, cold UI |

**Explicit non-goals for v1** (stated now so scope is honest): XFA forms, 3D/PRC content, embedded multimedia playback, PDF portfolios, and cloud collaboration servers. Each is either a legacy attack surface (XFA) or a separate product (collab backend). The annotation model is collaboration-*ready* (Section 4) without us shipping a server.

---

## 2. Canonical technology stack

| Technology | Role | Where used | Why this over the obvious alternative |
|---|---|---|---|
| **Rust** (stable, edition 2024) | Core systems language | Parser boundary, content-stream interpreter, tile scheduler, index, annotations, crypto | vs **C++**: the PDF parser is the single largest attack surface in the product; Rust eliminates memory-safety vulnerability classes by construction and gives data-race-free concurrency for the tile scheduler. C++'s only real advantage is PDFium itself — which we still use, wrapped, below. |
| **PDFium** (via audited Rust FFI bindings, sandboxed process) | PDF parsing foundation v1 | Object model, xref recovery, page tree, font programs, image decoders (incl. JBIG2/JPEG2000) | vs **MuPDF**: AGPL/commercial licensing is incompatible with a proprietary app. vs **greenfield Rust parser**: ISO 32000 is ~1,000 pages and the wild corpus is adversarial — a new parser is a 2–3 year correctness risk. PDFium is fuzzed daily by Chrome at scale. It sits behind an internal `PdfKernel` trait so it can be replaced incrementally by Rust components (content-stream interpreter first) without touching the rest of the system. |
| **wgpu** (Vulkan / Metal / DX12 backends) | GPU abstraction | All rasterization and compositing | vs **raw Metal + Vulkan**: one shader language (WGSL), one API, ~⅓ the backend maintenance. Cost: slower adoption of bleeding-edge backend features and some Android Vulkan driver fragility — mitigated by golden-image CI on a device farm and a CPU fallback path. |
| **Vello-class GPU compute vector rasterizer** (behind a `RasterBackend` trait) | Path/vector rasterization | Page content tiles, annotation ink | vs **Skia (CPU)**: compute-shader rasterization is what makes "re-render instead of upscale at 120 Hz" affordable — raster cost scales with screen pixels, not path count. Risk is real (Section 9); the `RasterBackend` trait allows Skia-CPU as a shipping fallback without architectural change. |
| **rustybuzz + ICU4X + fontations/skrifa** | Text shaping, Unicode segmentation/bidi, font loading | Text layer: extraction, selection, search offsets, reflow typesetting | vs **platform shapers (CoreText/DirectWrite)**: platform shapers disagree on line breaking and glyph advances, which would make selection rectangles and search hit-quads inconsistent across platforms. One deterministic stack = one source of truth. Platform shapers remain an escape hatch for scripts our stack mishandles. |
| **SwiftUI / Jetpack Compose** | UI chrome | Everything except the document canvas | vs **shared UI (Flutter/Qt)**: document chrome is <15% of the code; the canvas is custom-rendered regardless. Native UI buys accessibility trees, platform text input, drag-and-drop, and OS conventions for free — the things reviewers actually notice. |
| **UniFFI** | Native bridge | Rust core ↔ Swift/Kotlin view-models | vs **hand-written JNI/Swift FFI**: generated bindings eliminate an entire class of memory-management bugs at the boundary. vs **CXX**: C++-only. Known limitation — async ergonomics — handled by keeping all heavy objects behind opaque handles with a core-owned executor (Section 7). |
| **Tantivy** | Full-text inverted index | Per-library cross-document search | vs **SQLite FTS5**: segment-based incremental indexing (index while reading, merge in background), BM25 tuning, per-field boosts (body vs. annotation vs. OCR vs. outline), and pluggable CJK tokenizers. FTS5 is the documented v1 fallback if Tantivy's write amplification misbehaves on flash. |
| **SQLite (rusqlite) + SQLCipher** | Structured state | Annotation op-log, library metadata, reading positions, audit log | vs **redb/sled**: transactional integrity, 20 years of edge-case hardening, and a drop-in encrypted variant (SQLCipher) for the at-rest requirement. |
| **Platform OCR behind a trait**: Apple Vision / ML Kit, plus a bundled ONNX model (PaddleOCR-derived) for desktop | OCR | Scanned-document text layer, reflow of scans | vs **cloud OCR**: violates P3 and P6. vs **Tesseract everywhere**: measurably weaker on CJK and degraded scans; platform engines are free, fast, and NPU-accelerated. The bundled model keeps desktop parity. |
| **Custom op-log model → ISO 32000 annot export** | Annotation system | Section 4 | vs **mutating PDF annot dictionaries in place**: an append-only operation log gives undo, sync-mergeability, and audit for free; standard annots are a *serialization target*, not the storage format. |
| **RustCrypto (XChaCha20-Poly1305) + platform keystore/Keychain; webpki; OpenSSL CMS behind a narrow API for PAdES** | Security/crypto | At-rest encryption, signature creation/validation | vs **rolling our own CMS**: PDF digital signatures (PAdES/CMS) are a specialized, audit-worthy domain — we wrap a vetted implementation rather than write one, and flag it as licensed-component candidate (Section 9). |
| **Cargo workspace + xtask; GitHub Actions; Firebase Test Lab / device farm** | Build/CI | All platforms | vs **Bazel**: Cargo is idiomatic and sufficient at this team size; complexity budget is better spent on the device farm, where rendering bugs actually live. |
| **cargo-fuzz (libFuzzer) + AFL++; golden-image perceptual-diff harness; corpus from PDFium tests + PDF Association corpus** | Testing/fuzzing | Parser, content interpreter, font stack, image decoders, renderers | Fuzzing is not a phase, it is infrastructure from week 1 (Section 6). Golden images catch what fuzzing can't: silent rendering regressions. |

---

## 3. Rendering & viewing experience — the comfort/viewability differentiator

Framing: for each capability, the incumbent that sets the bar, and the concrete mechanism by which Meridian exceeds it.

| Capability | Current bar | Meridian mechanism | Target (with methodology) |
|---|---|---|---|
| Scroll/fling smoothness | PDF Expert | Velocity-predictive **tile** prefetch (below) | ≤1% dropped frames, 120 Hz, 2,000 px/s fling, 500-page doc (Perfetto trace) |
| Page-turn latency | Apple Books | Double-buffered spreads; incoming page fully resident in VRAM before the gesture starts; transition is a transform only | ≤4 ms commit, zero raster on critical path |
| Zoom crispness | Adobe Acrobat | Vector-first re-rasterization via GPU compute (below) | p95 re-raster ≤16 ms at 4×; mip fallback ≤2 frames |
| Color accuracy | Apple Preview (macOS) | End-to-end ICC pipeline (below) | ΔE2000 ≤2 on ICC test charts, colorimeter-verified, vs. reference renderer |
| Dark mode | Everyone — and nearly everyone fakes it by inversion | [CUT FROM ROADMAP] — Scope strictly focused on colorimetric ICC accuracy and paper/sepia eye-comfort | N/A (Cut from roadmap; strictly light/sepia palette) |
| Reflow | Adobe Liquid Mode (cloud, account-gated, phone-only) | On-device layout analysis (below + Section 4) | ≥95% reading-order accuracy on labeled academic corpus |
| Multi-document reading | Edge tabs / PDF Expert tabs | Shared-core panes + cross-document continuous scroll (below) | 3 open documents within the single-document memory ceiling |

### 3.1 Predictive tile prefetch

Each page is a mip pyramid of 256×256 device-pixel tiles (like a slippy-map, per page). The compositor samples from a GPU tile atlas; a scheduler decides what to rasterize next. The predictor is a **kinematic model fed by the platform scroller's own physics**: we sample touch/scroll velocity and deceleration and integrate forward 100–300 ms to predict the future viewport, then prioritize the prefetch queue by *predicted tile entry time × required mip level*. Fast fling → only low mips are requested (enough to avoid blanking); decelerating → high mips fill in before settle. PDF Expert prefetches whole pages; sub-page tile granularity with velocity-aware mip selection is strictly more work per frame avoided, and it is why the fling budget holds on a 500-page document.

### 3.2 Color-managed rendering and adaptive display modes

- **True color management.** Honor ICCBased color spaces and the document OutputIntent; render in extended-linear working space; output to the actual display profile (Display P3 on modern mobile, EDID/ICC on desktop). Apple Preview does this on macOS; essentially no Android viewer does — most assume sRGB end to end. We match Preview and win the platform it ignores.
- **Paper/sepia.** Applied as a transform in linear light, not a multiply on gamma-encoded sRGB — relative luminance contrast is preserved, so body text doesn't wash out. Text/vector and raster images are treated in separate passes (the content stream already tells us which is which).
- **True dark mode.** *[CUT FROM ROADMAP]* — In accordance with architectural refinement, True Dark Mode (the OKLab tone-mapping/scan-aware recoloring work) has been eliminated from the roadmap. The visual and adaptive pipeline is scoped exclusively to end-to-end ICC color fidelity and content-aware paper/sepia eye-comfort rendering.

### 3.3 Vector-first zoom

Zoom is a transform in the render graph, not a bitmap scale. On gesture end (or continuously, budget permitting), affected tiles are re-rasterized at the new effective DPI by the GPU compute rasterizer. During the gesture we show the nearest mip, capped at 1.5× upscale; the re-render must land within 2 frames at 120 Hz for typical page complexity. Adobe re-renders too, but with visible low-resolution dwell on complex pages; our budget makes that dwell a measured, enforced number rather than a hope.

### 3.4 Reflow / adaptive text

On-device layout analysis, no cloud: XY-cut whitespace segmentation → column detection via gap histograms → reading order from geometry + font-size clustering → classification of footnotes, figures, captions, and marginalia by position and font metrics. Scanned pages go through OCR first (with bounding boxes), then the same pipeline. The reflow view is **re-typeset** (extracted text, metric-matched fallback fonts) with figures anchored to the paragraph that references them (caption detection + "Figure N" resolution), footnotes rendered as inline expanders, and **synchronized highlighting** between original and reflow so the reader never loses place. Liquid Mode is the bar; we exceed it on privacy (on-device), availability (no account), and place-keeping (synchronized views). Honest flag: two-column academic layout with float-heavy figures is the worst case, and 95% reading-order accuracy is a target to be proven in Phase 6, not a given.

### 3.5 Eye comfort with actual mechanisms

Ambient light sensor + per-page content luminance histogram feed an adaptive contrast curve applied in the display shader — image-heavy pages get gentler treatment than text pages to protect fidelity. Color-temperature shifting uses a melanopic-weighted model in linear light on a schedule, per-content-type in strength. This is categorically different from a brightness slider or a system-wide tint overlay: it is content-aware and color-managed.

### 3.6 Multi-document

Each pane is an independent viewport into the shared core; tile caches for the same document are shared across panes. **Reading queue**: a user-defined list of documents presented as one continuous scroll — page indices are namespaced per document, search results across the queue are navigable without leaving the scroll. No mainstream viewer offers seamless cross-document continuous reading with unified search. The memory governor (Section 5) treats N open documents as one budget, which is what makes this safe on a 4 GB device.

---

## 4. Feature architecture — the richness differentiator

For each feature: the data model first, UI second.

### 4.1 Annotations

- **Model**: append-only operation log. `AnnotOp { op_id: (actor_id, counter), doc_id, page, kind, geometry (paths/quads with pressure+tilt for ink), style, created, vclock, tombstone? }`. Stored in SQLite; ops are commutative and mergeable (CRDT semantics; Automerge document per PDF when sync is enabled).
- **Rendering**: annotations rasterize into a separate overlay tile layer composited above page tiles — annotation edits never invalidate the page tile cache.
- **Export**: ops materialize to standard ISO 32000 annotation dictionaries with generated appearance streams — ink→`Ink`/`InkList`, highlights→`Highlight`/`QuadPoints`, notes→`Text`/`FreeText`, shapes→`Square`/`Circle`/`Line`, stamps→`Stamp`. Round-trip fidelity through Adobe Acrobat without warnings is a Phase-3 exit criterion.
- **Why this beats the bar** (PDF Expert's ink, which is excellent but proprietary-sync only): mergeable ops + lossless standards round-trip + full undo history.

### 4.2 Search

- **Index**: Tantivy, one library index + ephemeral per-document segments. Fields: `body` (with page + char-offset mapping to glyph-run quads for on-page highlighting), `annotations`, `ocr`, `metadata`, `outline`. Incremental: background indexing on first open, front-loaded to early pages; segment merges off the UI thread.
- **Cross-document**: the library index *is* the cross-document index; the reading queue (3.6) consumes it directly.
- **Semantic layer (optional, P6)**: a small on-device multilingual embedding model, vectors in an HNSW index, chunked by layout-aware blocks from the reflow analyzer. Removable without touching the deterministic core; clearly labeled in UI as on-device and optional.

### 4.3 Forms, signatures, redaction — and tamper-evidence

- **Forms**: AcroForm widget model → per-field view-models with type validation. XFA: out of scope (P-non-goals), and the app says so plainly when it encounters it.
- **Signatures**: (a) drawn/typed signature *appearances* (annotations); (b) real PAdES/CMS signatures using keys from the platform keystore or imported PKCS#12. Signing uses **incremental updates** (append-only revisions per ISO 32000), so each signature's `ByteRange` digest covers exactly the bytes it signed. The UI exposes a **revision timeline**: "what changed since signature N" — answered by running the diff engine (4.4) between revisions. Tamper-evidence is thus structural, not a badge.
- **Redaction**: true redaction, not black boxes. Mark → resolve affected content (text runs, image regions, annotations, metadata, embedded files, thumbnails) → excise from content streams and resource dictionaries → rewrite streams → sanitize metadata → re-linearize → **verify by re-extracting text and asserting absence** → optional re-sign. Every redaction is recorded in a hash-chained per-document audit log.

### 4.4 Version / diff view

Three levels, composed: (1) **page alignment** via perceptual hashes + text shingles (handles inserted/removed pages); (2) **operator-level text diff** — tokenize both content streams into positioned words, LCS-diff them → word-level add/remove with page coordinates; (3) **pixel diff in OKLab**, clustered into regions, for graphics/images. Output is a change list (page, region, type, before/after text) navigable like search results. The bar (Adobe Compare, Draftable) is desktop-only and slow; ours is on-device and reuses the same machinery that powers the signature revision timeline.

### 4.5 Outline/TOC generation

For documents with no outline: heading detection via font-size/weight clustering + numbering-pattern recognition (`1.`, `1.1`, `Chapter`) + page-position consistency → editable outline tree. Nobody in the mainstream set does this well; it is a differentiator with medium risk (Section 9), and it ships as *suggested* outline the user can accept/edit, never silently asserted.

### 4.6 Citations and cross-document linking (v2, deterministic)

Reference-section detection → entry parsing → DOI/arXiv/ISBN extraction via patterns → in-document `[12]` markers link to entries; if a referenced work exists in the local library (fuzzy title/DOI match), a bidirectional link is created. No AI required; clearly v2.

---

## 5. Caching & memory strategy

**Hard rule: no unbounded in-memory page objects.** Every cache has a byte budget enforced by a single **memory governor** that owns all allocation accounting.

| Tier | Contents | Budget (4 GB device, single doc) | Eviction |
|---|---|---|---|
| L0 — GPU tile atlas | Tiles being composited + immediate neighbors | 96 MB VRAM | LRU within predicted-viewport ring; zoom change invalidates by mip |
| L1 — near-viewport bitmaps | Decoded tiles ±N pages (N velocity-adaptive) | 96 MB RAM | Ring eviction; N shrinks under pressure |
| L2 — decoded resources | Fonts, ICC profiles, image XObjects, keyed by object ID, **shared across open documents** | 32 MB | Global LRU |
| L3 — parsed page graphs | Page object trees | ≤ 8 pages, 16 MB | Hard LRU cap; re-parse on demand (parse ≪ raster cost) |
| L4 — persistent | Tantivy index, fixed-size page thumbnails (WebP), annotation op-log, reading positions | Disk, bounded per doc | User-controlled |

**Eviction is driven by device signals, not just LRU**: OS memory-pressure callbacks (iOS `didReceiveMemoryWarning`, Android `onTrimMemory` levels) map to tier-shrink percentages; **thermal state** reduces prefetch depth and suspends background indexing; **zoom level** shifts budget between mips and page count; **scroll velocity** shifts it between depth (slow) and breadth (fling). Example policy: `TRIM_MEMORY_RUNNING_LOW` → L1 halves, L2 evicts 50%, prefetch depth → 1 page. The 250 MB ceiling in Section 1 is the sum of these budgets and is soak-tested under forced pressure.

---

## 6. Security & trust model

- **Every input is hostile.** The parser boundary, content-stream interpreter, font stack, and image decoders (JBIG2 and JPEG2000 get named explicitly — both have rich CVE histories) are fuzzed continuously with libFuzzer + AFL++, seeded from the PDFium and PDF Association corpora, with crash-triage CI. Differential fuzzing compares our interpreter's output against PDFium's.
- **Isolation.** PDFium runs in a separate sandboxed process (seccomp where available; the mobile app sandbox otherwise) communicating over a narrow IPC. A compromise of the parser does not reach the UI, the keystore, or the network.
- **Risky content policy**: embedded JavaScript — **no JS engine is shipped, period**; `Launch`/`URI`/GoToR actions require explicit consent showing the destination; remote streams and external references are off by default.
- **At rest**: SQLCipher / XChaCha20-Poly1305 for all local state; keys custodied by platform keystore/Keychain; the search index is encrypted like everything else.
- **Supply chain**: `cargo-deny` license/advisory gates, SBOM per release, signed and reproducible release builds.

**What Meridian will never do silently**: transmit document content or derived data off-device (telemetry is opt-in and content-free); execute embedded scripts; follow embedded links; auto-trust any signature (chain validation is always shown); treat redaction as cosmetic; modify a signed revision (all edits are new incremental revisions).

---

## 7. Cross-platform strategy

Roughly **75% of the code is the shared Rust core**; platform layers are thin and replaceable.

| Layer | Shared Rust core | Platform-specific |
|---|---|---|
| Parsing, render graph, tile scheduler, search, annotations, layout analysis, crypto, diff | ✅ | — |
| GPU backend | ✅ (wgpu) | Surface creation only (`CAMetalLayer`, `SurfaceView`) |
| UI chrome, input, accessibility | — | SwiftUI / Compose |
| Keystore, OCR engine, file providers, ambient light | Core trait | Platform implementation |

**Boundary**: a UniFFI-generated API of ~40–60 types. All heavy objects live behind opaque handles; async work runs on a core-owned executor and completes into platform futures via callbacks. Rule: no platform type crosses into the core, no core type escapes unwrapped. Desktop follows: macOS is nearly free (same Swift layer), Windows/Linux need new UI shells over the already-portable wgpu core. iOS + Android are the committed first two platforms; the same core powers both from day one — that is the test that the abstraction boundary is real.

---

## 8. Build order / phased roadmap

Sequenced by **validation risk**: each phase must be proven correct and performant before the next builds on it. Exit criteria are testable by a solo builder.

| Phase | Scope | Why now (risk retired) | Exit criteria |
|---|---|---|---|
| **0 — Spike** (2–4 wks) | PDFium behind Rust FFI, sandboxed process, render pages to bitmaps on desktop | Validates the riskiest assumption: that wrapped PDFium meets memory/latency behavior at all | 1,000-file adversarial corpus renders without crash; p95 single-page raster <150 ms @150 DPI; RSS profiled |
| **1 — Rendering core** | Tile pipeline, GPU compositor, scroll/zoom, prefetch, memory governor — **one platform** | The entire product thesis (P2, P4, budgets) stands or falls here | 120 Hz fling ≤1% dropped frames (Section 1 methodology); 250 MB ceiling holds under forced memory pressure; cold-open ≤300 ms met or budget renegotiated *now*, not later |
| **2 — Text layer** | Extraction, shaping, selection, Tantivy search | Everything above (annotations, reflow, diff) consumes positioned text | Extraction fidelity ≥99% vs. reference on corpus; search first-result ≤100 ms on 10k pages; CJK + RTL selection correct |
| **3 — Annotations** | Op-log model, overlay tiles, ink, ISO 32000 export | First user-visible differentiator; validates the op-log pattern reused by redaction/audit | Round-trip through Acrobat with zero loss/warnings; 1M-op merge/undo stress test |
| **4 — Color & adaptive modes** | ICC pipeline, paper & sepia modes (Dark mode cut) | Colorimetric accuracy and content-aware eye comfort | ΔE2000 ≤2 on standard ICC color charts; paper/sepia melanopic & chromatic adaptation |
| **5 — Forms / signatures / redaction** | AcroForms, PAdES, true redaction + audit log | Security-critical; built only after fuzzing infrastructure has months of mileage | External PAdES validator passes; redaction verified by extraction-absence; 30 days fuzz-clean |
| **6 — Reflow & outline** | Layout analysis, reflow view, TOC generation | Highest uncertainty (Section 9); prototyped here with real corpus metrics | ≥95% reading-order accuracy on labeled corpus; human eval on 100 worst-case academic PDFs |
| **7 — Second platform + library** | Port UI shell, cross-doc index, reading queue; **optional AI layer last** | Proves the 75% claim; AI rides on a finished deterministic core | Second platform passes the same Phase-1 budgets; removing the AI module changes zero core tests |

---

## 9. Where this design is genuinely uncertain

| Component | Verdict | What would de-risk it |
|---|---|---|
| Velocity-predictive tile prefetch | **Plausible** — proven pattern in map renderers; novel only in its application to paged documents | Phase 1 trace-driven replay against recorded fling sessions |
| Color-managed pipeline, sepia, op-log annotations, on-device OCR, cross-doc index | **Plausible** — each exists somewhere in industry; the work is integration and discipline | Already covered by phase exit criteria |
| 300 ms cold open on 50 MB scanned files | **Plausible but tight** — the number is dominated by JPEG2000/JBIG2 decode, not our code | Phase 0 must measure decode latency distribution; if blown, budget renegotiated with data before Phase 1 builds on it |
| GPU compute rasterization of *arbitrary* PDF content (shadings, transparency groups, blend modes, Type 3 fonts) at 120 Hz budgets | **Aspirational** — Vello-class tech is young; PDF's full imaging model is larger than any current compute renderer's feature set | `RasterBackend` trait means Skia-CPU fallback ships if needed; prototype the 20 nastiest known test files in Phase 1 |
| True dark mode for scanned pages (edge-aware background lift) | **Cut from roadmap** | Eliminated to maintain architectural focus on 100% ICC color fidelity and paper/sepia modes |
| Reflow of two-column, float-heavy academic PDFs with figure anchoring | **Aspirational** — layout-analysis accuracy is a research-grade problem; 95% is a target, not a certainty | Phase 6 corpus metrics before any UI commitment; ship as "beta" quality-labeled |
| Word-level content-stream diff | **Plausible-leaning-aspirational** — alignment ambiguity on re-encoded streams | Fall back gracefully to region-level pixel diff when operator alignment confidence is low |
| PAdES/CMS signing in Rust | **Plausible but specialized** — the crate ecosystem here is immature | Budget for wrapping a vetted (possibly licensed) component; do not write CMS from scratch |

---

## Feasibility — honest assessment

Team size and timeline were not stated, so this is conditional: for a **solo builder or a 2–3 person team**, Phases 0–3 (a fast, correct, beautiful reader with best-in-class annotations) is a realistic **9–12 month** effort to a credible MVP, with Phases 4–7 adding another 9–12 months — and the honest caveat is that the two aspirational rendering bets (GPU compute rasterization of the full PDF imaging model, and scan-aware dark mode) are the kind of problems that can absorb an unbounded amount of time if the fallback plans aren't exercised early and without ego. For a funded team of 6–10, the full roadmap is an aggressive-but-sane 18–24 months. **What is your team size and target timeline?** The answer changes which fallback paths should be exercised from day one versus kept in reserve.
