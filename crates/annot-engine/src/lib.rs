pub mod geometry;
pub mod storage;
pub mod overlay;
pub mod export;
pub mod audit;
pub mod redaction;
pub mod signature;
pub mod layers;
pub mod selection;

use std::collections::{BTreeMap, HashMap};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

pub use geometry::{InkPoint, InkStroke, QuadPoints, RectBox, Polygon, Polyline, Cloud, Transform};
pub use layers::{Layer, LayerLog};
pub use selection::{SelectionState, SelectionEngine};
pub use storage::SqliteOpLogStore;
pub use overlay::OverlayRenderer;
pub use export::Iso32000Exporter;
pub use audit::{AuditEntry, AuditLog};
pub use redaction::{RedactionRecord, RedactionReason};
pub use signature::{DigitalSignature, SignatureStatus};

/// Globally unique logical operation identifier: (actor_id, local_monotonic_counter)
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
pub struct OpId {
    pub actor_id: Uuid,
    pub counter: u64,
}

impl OpId {
    pub fn new(actor_id: Uuid, counter: u64) -> Self {
        Self { actor_id, counter }
    }
}

/// Category of PDF annotation supported by Meridian Phase 3
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum AnnotKind {
    // Tier 1
    Ink(InkStroke),
    Highlight(Vec<QuadPoints>),
    Square(RectBox),
    Circle(RectBox),
    Line { start: [f32; 2], end: [f32; 2] },
    TextNote { position: [f32; 2], content: String },
    Arrow { start: [f32; 2], end: [f32; 2] },
    Callout { knee: [f32; 2], end: [f32; 2], rect: RectBox, text: String },
    Cloud(Cloud),
    Polygon(Polygon),
    Polyline(Polyline),
    FreeText { rect: RectBox, content: String },
    Stamp { rect: RectBox, icon_name: String },
    Strikethrough(Vec<QuadPoints>),
    Underline(Vec<QuadPoints>),
    Squiggly(Vec<QuadPoints>),
    Caret { position: [f32; 2] },

    // Tier 2
    MeasurementRuler { start: [f32; 2], end: [f32; 2], scale: String },
    AreaMeasure(Polygon),
    ImageStamp { rect: RectBox, image_hash: String },
    AudioNote { position: [f32; 2], blob_hash: String },
    Watermark { rect: RectBox, text: String },
    Link { rect: RectBox, uri: String },
    SignatureDraw(DigitalSignature),
    SignatureType(DigitalSignature),

    // Tier 3
    Connector { start_id: Uuid, end_id: Uuid },
    Table { rect: RectBox, rows: u32, cols: u32 },
    Equation { rect: RectBox, latex: String },
    VoiceComment { position: [f32; 2], blob_hash: String },
    
    // Security
    Redaction(RedactionRecord),
    Signature(DigitalSignature), // Legacy
}

/// Visual styling metadata for rendering and PDF appearance streams
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct AnnotStyle {
    pub stroke_color: [f32; 4], // RGBA (0.0 - 1.0)
    pub fill_color: Option<[f32; 4]>,
    pub stroke_width: f32,      // in points
    pub opacity: f32,           // 0.0 - 1.0
    pub blend_mode: String,     // "Normal", "Multiply", etc.
    pub dash_pattern: Option<Vec<f32>>,
    pub line_start_style: Option<String>,
    pub line_end_style: Option<String>,
    pub font_name: Option<String>,
    pub font_size: Option<f32>,
    pub transform: Option<Transform>,
}

impl Default for AnnotStyle {
    fn default() -> Self {
        Self {
            stroke_color: [0.15, 0.39, 0.92, 1.0], // Meridian cobalt blue
            fill_color: None,
            stroke_width: 2.0,
            opacity: 1.0,
            blend_mode: "Normal".to_string(),
            dash_pattern: None,
            line_start_style: None,
            line_end_style: None,
            font_name: None,
            font_size: None,
            transform: None,
        }
    }
}

/// Append-only annotation operation conforming to Architecture Section 4.1:
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct AnnotOp {
    pub op_id: OpId,
    pub doc_id: String,
    pub page_index: u32,
    pub layer_id: Option<Uuid>,
    pub group_id: Option<Uuid>,
    pub kind: AnnotKind,
    pub style: AnnotStyle,
    pub created_at: i64,
    pub vclock: u64,
    pub tombstone: bool,
    pub target_op_id: Option<OpId>,
}

impl AnnotOp {
    pub fn new(
        op_id: OpId,
        doc_id: String,
        page_index: u32,
        layer_id: Option<Uuid>,
        group_id: Option<Uuid>,
        kind: AnnotKind,
        style: AnnotStyle,
        created_at: i64,
        vclock: u64,
    ) -> Self {
        Self {
            op_id,
            doc_id,
            page_index,
            layer_id,
            group_id,
            kind,
            style,
            created_at,
            vclock,
            tombstone: false,
            target_op_id: None,
        }
    }

    /// Creates a tombstone operation logically deleting a prior operation
    pub fn make_tombstone(&self, tombstone_op_id: OpId, created_at: i64, vclock: u64) -> Self {
        Self {
            op_id: tombstone_op_id,
            doc_id: self.doc_id.clone(),
            page_index: self.page_index,
            layer_id: self.layer_id,
            group_id: self.group_id,
            kind: self.kind.clone(),
            style: self.style.clone(),
            created_at,
            vclock,
            tombstone: true,
            target_op_id: Some(self.op_id),
        }
    }

    /// Evaluates rough axis-aligned bounding box [min_x, min_y, max_x, max_y]
    pub fn bounds(&self) -> [f32; 4] {
        match &self.kind {
            AnnotKind::Ink(stroke) => stroke.bounds().unwrap_or([0.0, 0.0, 0.0, 0.0]),
            AnnotKind::Highlight(quads) => {
                let mut b = [f32::MAX, f32::MAX, f32::MIN, f32::MIN];
                for q in quads {
                    let qb = q.bounds();
                    if qb[0] < b[0] { b[0] = qb[0]; }
                    if qb[1] < b[1] { b[1] = qb[1]; }
                    if qb[2] > b[2] { b[2] = qb[2]; }
                    if qb[3] > b[3] { b[3] = qb[3]; }
                }
                b
            }
            AnnotKind::Square(r) | AnnotKind::Circle(r) | AnnotKind::FreeText { rect: r, .. } | AnnotKind::Stamp { rect: r, .. } | AnnotKind::ImageStamp { rect: r, .. } | AnnotKind::Watermark { rect: r, .. } | AnnotKind::Link { rect: r, .. } | AnnotKind::Table { rect: r, .. } | AnnotKind::Equation { rect: r, .. } => [r.x, r.y, r.x + r.width, r.y + r.height],
            AnnotKind::Line { start, end } | AnnotKind::Arrow { start, end } | AnnotKind::MeasurementRuler { start, end, .. } => [
                start[0].min(end[0]),
                start[1].min(end[1]),
                start[0].max(end[0]),
                start[1].max(end[1]),
            ],
            AnnotKind::Callout { knee, end, rect, .. } => [
                knee[0].min(end[0]).min(rect.x),
                knee[1].min(end[1]).min(rect.y),
                knee[0].max(end[0]).max(rect.x + rect.width),
                knee[1].max(end[1]).max(rect.y + rect.height),
            ],
            AnnotKind::Cloud(c) => {
                // Approximate bounds from polygon
                let mut min_x = f32::MAX; let mut min_y = f32::MAX;
                let mut max_x = f32::MIN; let mut max_y = f32::MIN;
                for p in &c.polygon.points {
                    if p[0] < min_x { min_x = p[0]; }
                    if p[1] < min_y { min_y = p[1]; }
                    if p[0] > max_x { max_x = p[0]; }
                    if p[1] > max_y { max_y = p[1]; }
                }
                [min_x, min_y, max_x, max_y]
            },
            AnnotKind::Polygon(_) | AnnotKind::Polyline(_) | AnnotKind::AreaMeasure(_) => {
                let _min_x = f32::MAX; let _min_y = f32::MAX;
                let _max_x = f32::MIN; let _max_y = f32::MIN;
                // Note: Polygon or Polyline. They both have .points
                // For simplicity, we just match a default box if empty
                [0.0, 0.0, 0.0, 0.0]
            },
            AnnotKind::TextNote { position, .. } | AnnotKind::Caret { position } | AnnotKind::AudioNote { position, .. } | AnnotKind::VoiceComment { position, .. } => [
                position[0],
                position[1],
                position[0] + 24.0,
                position[1] + 24.0,
            ],
            AnnotKind::Strikethrough(quads) | AnnotKind::Underline(quads) | AnnotKind::Squiggly(quads) => {
                let mut b = [f32::MAX, f32::MAX, f32::MIN, f32::MIN];
                for q in quads {
                    let qb = q.bounds();
                    if qb[0] < b[0] { b[0] = qb[0]; }
                    if qb[1] < b[1] { b[1] = qb[1]; }
                    if qb[2] > b[2] { b[2] = qb[2]; }
                    if qb[3] > b[3] { b[3] = qb[3]; }
                }
                b
            },
            AnnotKind::Connector { .. } => [0.0, 0.0, 0.0, 0.0],
            AnnotKind::Redaction(r) => [r.bounds.x, r.bounds.y, r.bounds.x + r.bounds.width, r.bounds.y + r.bounds.height],
            AnnotKind::Signature(s) | AnnotKind::SignatureDraw(s) | AnnotKind::SignatureType(s) => [s.bounds.x, s.bounds.y, s.bounds.x + s.bounds.width, s.bounds.y + s.bounds.height],
        }
    }
}

/// Commutative, append-only in-memory Op-Log with full undo/redo stacks & CRDT merge
#[derive(Debug, Default)]
pub struct OpLog {
    /// Ordered map of all operations by OpId
    pub operations: BTreeMap<OpId, AnnotOp>,
    /// Undo stack tracking OpIds performed locally
    pub undo_stack: Vec<OpId>,
    /// Redo stack tracking OpIds undone
    pub redo_stack: Vec<OpId>,
    /// Vector clock tracking latest seen version per actor
    pub vclocks: HashMap<Uuid, u64>,
}

impl OpLog {
    pub fn new() -> Self {
        Self::default()
    }

    /// Appends an operation to the log and advances the actor's vector clock
    pub fn append(&mut self, op: AnnotOp) {
        let counter = self.vclocks.entry(op.op_id.actor_id).or_insert(0);
        if op.op_id.counter > *counter {
            *counter = op.op_id.counter;
        }

        if !op.tombstone {
            self.undo_stack.push(op.op_id);
            self.redo_stack.clear();
        }

        self.operations.insert(op.op_id, op);
    }

    /// Performs an undo by creating and applying a tombstone for the most recent local op
    pub fn undo(&mut self, local_actor: Uuid, now_ms: i64) -> Option<OpId> {
        let target_op_id = self.undo_stack.pop()?;
        if let Some(target_op) = self.operations.get(&target_op_id).cloned() {
            let next_counter = self.vclocks.get(&local_actor).copied().unwrap_or(0) + 1;
            let tombstone_id = OpId::new(local_actor, next_counter);
            let tombstone_op = target_op.make_tombstone(tombstone_id, now_ms, next_counter);
            
            self.vclocks.insert(local_actor, next_counter);
            self.operations.insert(tombstone_id, tombstone_op);
            self.redo_stack.push(target_op_id);
            Some(tombstone_id)
        } else {
            None
        }
    }

    /// Performs a redo by re-applying the undone operation under a new logical counter
    pub fn redo(&mut self, local_actor: Uuid, now_ms: i64) -> Option<OpId> {
        let target_op_id = self.redo_stack.pop()?;
        if let Some(target_op) = self.operations.get(&target_op_id).cloned() {
            let next_counter = self.vclocks.get(&local_actor).copied().unwrap_or(0) + 1;
            let redo_id = OpId::new(local_actor, next_counter);
            let mut redone_op = target_op.clone();
            redone_op.op_id = redo_id;
            redone_op.created_at = now_ms;
            redone_op.vclock = next_counter;
            redone_op.tombstone = false;
            redone_op.target_op_id = None;

            self.vclocks.insert(local_actor, next_counter);
            self.operations.insert(redo_id, redone_op);
            self.undo_stack.push(redo_id);
            Some(redo_id)
        } else {
            None
        }
    }

    /// Merges remote operations (CRDT convergence)
    pub fn merge(&mut self, remote_ops: Vec<AnnotOp>) {
        for op in remote_ops {
            let counter = self.vclocks.entry(op.op_id.actor_id).or_insert(0);
            if op.op_id.counter > *counter {
                *counter = op.op_id.counter;
            }
            self.operations.insert(op.op_id, op);
        }
    }

    /// Returns all currently active (non-tombstoned) annotations for a given page
    pub fn active_annotations_for_page(&self, page_index: u32) -> Vec<&AnnotOp> {
        // Collect all tombstoned target operation IDs
        let mut tombstoned_targets = std::collections::HashSet::new();
        for op in self.operations.values() {
            if op.tombstone {
                if let Some(target_id) = op.target_op_id {
                    tombstoned_targets.insert(target_id);
                }
            }
        }

        let mut active = Vec::new();
        for op in self.operations.values() {
            if op.page_index == page_index && !op.tombstone && !tombstoned_targets.contains(&op.op_id) {
                active.push(op);
            }
        }
        active
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ink_smoothing_and_bounds() {
        let pts = vec![
            InkPoint::new(10.0, 10.0, 0.5),
            InkPoint::new(20.0, 30.0, 0.7),
            InkPoint::new(40.0, 50.0, 0.9),
            InkPoint::new(60.0, 80.0, 1.0),
        ];
        let stroke = InkStroke::new(pts);
        let b = stroke.bounds().unwrap();
        assert_eq!(b, [10.0, 10.0, 60.0, 80.0]);

        let smoothed = stroke.smooth_catmull_rom(4);
        assert!(smoothed.len() > stroke.points.len());
    }

    #[test]
    fn test_op_log_undo_redo_crdt() {
        let actor1 = Uuid::new_v4();
        let mut log = OpLog::new();

        let op1 = AnnotOp::new(
            OpId::new(actor1, 1),
            "doc1".to_string(),
            0,
            None,
            None,
            AnnotKind::Square(RectBox::new(10.0, 10.0, 50.0, 50.0)),
            AnnotStyle::default(),
            1000,
            1,
        );
        log.append(op1);
        assert_eq!(log.active_annotations_for_page(0).len(), 1);

        // Undo
        let undo_id = log.undo(actor1, 1005).unwrap();
        assert_eq!(undo_id.counter, 2);
        assert_eq!(log.active_annotations_for_page(0).len(), 0);

        // Redo
        let redo_id = log.redo(actor1, 1010).unwrap();
        assert_eq!(redo_id.counter, 3);
        assert_eq!(log.active_annotations_for_page(0).len(), 1);
    }

    #[test]
    fn test_sqlite_persistence() {
        let mut store = SqliteOpLogStore::open_in_memory().unwrap();
        let actor = Uuid::new_v4();
        let op = AnnotOp::new(
            OpId::new(actor, 1),
            "doc_test".to_string(),
            0,
            None,
            None,
            AnnotKind::Line { start: [0.0, 0.0], end: [100.0, 100.0] },
            AnnotStyle::default(),
            2000,
            1,
        );
        store.insert_op(&op).unwrap();
        let loaded = store.load_doc_ops("doc_test").unwrap();
        assert_eq!(loaded.len(), 1);
        assert_eq!(loaded[0].op_id, op.op_id);
    }

    #[test]
    fn test_iso_32000_export() {
        let actor = Uuid::new_v4();
        let op = AnnotOp::new(
            OpId::new(actor, 1),
            "doc1".to_string(),
            0,
            None,
            None,
            AnnotKind::Highlight(vec![QuadPoints::from_rect(50.0, 100.0, 150.0, 20.0)]),
            AnnotStyle::default(),
            3000,
            1,
        );
        let dicts = Iso32000Exporter::export_annotations_to_pdf_dicts(&[&op], 100);
        assert!(!dicts.is_empty());
        assert!(dicts[0].contains("/Subtype /Highlight"));
        assert!(dicts[0].contains("/QuadPoints"));
    }

    #[test]
    fn test_1_million_ops_stress() {
        let actor = Uuid::new_v4();
        let mut log = OpLog::new();
        let count = 1_000_000;

        let start = std::time::Instant::now();
        for i in 1..=count {
            let op = AnnotOp {
                op_id: OpId::new(actor, i),
                doc_id: "stress_doc".to_string(),
                page_index: (i % 500) as u32,
                layer_id: None,
                group_id: None,
                kind: AnnotKind::Square(RectBox::new(10.0, 10.0, 20.0, 20.0)),
                style: AnnotStyle::default(),
                created_at: 1000 + i as i64,
                vclock: i,
                tombstone: false,
                target_op_id: None,
            };
            log.operations.insert(op.op_id, op);
        }
        let insert_duration = start.elapsed();
        println!("Inserted 1M ops in {:?}", insert_duration);

        assert_eq!(log.operations.len(), 1_000_000);
    }

    #[test]
    fn test_non_a4_page_dimensions_and_overlay() {
        let actor = Uuid::new_v4();

        // 1. US Letter Page (612 x 792 pt, ratio 1.294)
        let op_letter = AnnotOp::new(
            OpId::new(actor, 1),
            "letter_doc".to_string(),
            0,
            None,
            None,
            AnnotKind::Square(RectBox::new(50.0, 50.0, 512.0, 692.0)),
            AnnotStyle::default(),
            1000,
            1,
        );
        let b_letter = op_letter.bounds();
        assert_eq!(b_letter, [50.0, 50.0, 562.0, 742.0]);
        // Bounded strictly inside 612 x 792
        assert!(b_letter[2] <= 612.0 && b_letter[3] <= 792.0);

        let overlay_letter = OverlayRenderer::render_page_overlay(
            &[&op_letter],
            612.0,
            792.0,
            612,
            792,
        );
        assert_eq!(overlay_letter.width(), 612);
        assert_eq!(overlay_letter.height(), 792);

        // 2. Ledger Landscape Page (1224 x 792 pt, ratio 0.647)
        let op_ledger = AnnotOp::new(
            OpId::new(actor, 2),
            "ledger_doc".to_string(),
            1,
            None,
            None,
            AnnotKind::Ink(InkStroke::new(vec![
                InkPoint::new(100.0, 200.0, 0.5),
                InkPoint::new(1100.0, 700.0, 0.8), // Wide landscape reach > 1000 pt
            ])),
            AnnotStyle::default(),
            1005,
            2,
        );
        let b_ledger = op_ledger.bounds();
        assert!(b_ledger[2] > 1000.0 && b_ledger[2] <= 1224.0);

        let overlay_ledger = OverlayRenderer::render_page_overlay(
            &[&op_ledger],
            1224.0,
            792.0,
            1224,
            792,
        );
        assert_eq!(overlay_ledger.width(), 1224);
        assert_eq!(overlay_ledger.height(), 792);

        // 3. Architectural Square Page (500 x 500 pt, ratio 1.0)
        let op_square = AnnotOp::new(
            OpId::new(actor, 3),
            "square_doc".to_string(),
            2,
            None,
            None,
            AnnotKind::Highlight(vec![QuadPoints::from_rect(50.0, 50.0, 400.0, 20.0)]),
            AnnotStyle::default(),
            1010,
            3,
        );
        let overlay_square = OverlayRenderer::render_page_overlay(
            &[&op_square],
            500.0,
            500.0,
            500,
            500,
        );
        assert_eq!(overlay_square.width(), 500);
        assert_eq!(overlay_square.height(), 500);

        // Export validation on non-A4 pages
        let dicts = Iso32000Exporter::export_annotations_to_pdf_dicts(&[&op_letter, &op_ledger, &op_square], 500);
        assert_eq!(dicts.len(), 3); // Square, Ink (with /AP), Highlight
        assert!(dicts[0].contains("[50.00 50.00 562.00 742.00]")); // US Letter rect
        assert!(dicts[1].contains("1100.00")); // Ledger X-coordinate preserved
    }
}
