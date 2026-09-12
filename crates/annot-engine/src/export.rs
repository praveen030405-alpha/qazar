use crate::{AnnotKind, AnnotOp};

/// Materializes annotation operations into standard ISO 32000 annotation dictionaries
/// Architecture Section 4.1: "Export: ops materialize to standard ISO 32000 annotation dictionaries with generated appearance streams — ink→Ink/InkList, highlights→Highlight/QuadPoints, notes→Text/FreeText, shapes→Square/Circle/Line, stamps→Stamp. Round-trip fidelity through Adobe Acrobat without warnings is a Phase-3 exit criterion."
pub struct Iso32000Exporter;

impl Iso32000Exporter {
    /// Generates ISO 32000 compliant PDF object strings for all active annotations
    pub fn export_annotations_to_pdf_dicts(ops: &[&AnnotOp], starting_obj_id: u32) -> Vec<String> {
        let mut obj_id = starting_obj_id;
        let mut dicts = Vec::new();

        for op in ops {
            if op.tombstone {
                continue;
            }

            let b = op.bounds();
            let rect_str = format!("[{:.2} {:.2} {:.2} {:.2}]", b[0], b[1], b[2], b[3]);
            let r = op.style.stroke_color[0];
            let g = op.style.stroke_color[1];
            let b_col = op.style.stroke_color[2];
            let color_str = format!("[{:.3} {:.3} {:.3}]", r, g, b_col);

            match &op.kind {
                AnnotKind::Ink(stroke) => {
                    let mut ink_list_str = String::from("[ [");
                    for pt in &stroke.points {
                        ink_list_str.push_str(&format!("{:.2} {:.2} ", pt.x, pt.y));
                    }
                    ink_list_str.push_str("] ]");

                    let mut stream_content = format!(
                        "q\n{:.3} {:.3} {:.3} RG\n{:.2} w\n1 J\n1 j\n",
                        r, g, b_col, op.style.stroke_width
                    );
                    for (i, pt) in stroke.points.iter().enumerate() {
                        if i == 0 {
                            stream_content.push_str(&format!("{:.2} {:.2} m\n", pt.x - b[0], pt.y - b[1]));
                        } else {
                            stream_content.push_str(&format!("{:.2} {:.2} l\n", pt.x - b[0], pt.y - b[1]));
                        }
                    }
                    stream_content.push_str("S\nQ\n");

                    let ap_obj_id = obj_id + 1;
                    let dict = format!(
                        "{} 0 obj\n<<\n  /Type /Annot\n  /Subtype /Ink\n  /Rect {}\n  /C {}\n  /InkList {}\n  /BS << /W {:.2} >>\n  /CA {:.2}\n  /AP << /N {} 0 R >>\n>>\nendobj\n\
                        {} 0 obj\n<<\n  /Type /XObject\n  /Subtype /Form\n  /BBox [0 0 {:.2} {:.2}]\n  /Length {}\n>>\nstream\n{}\nendstream\nendobj\n",
                        obj_id, rect_str, color_str, ink_list_str, op.style.stroke_width, op.style.opacity, ap_obj_id,
                        ap_obj_id, b[2] - b[0], b[3] - b[1], stream_content.len(), stream_content
                    );
                    dicts.push(dict);
                    obj_id += 2;
                }
                AnnotKind::Highlight(quads) => {
                    let mut quad_str = String::from("[");
                    for quad in quads {
                        for coord in &quad.coords {
                            quad_str.push_str(&format!("{:.2} ", coord));
                        }
                    }
                    quad_str.push(']');

                    let dict = format!(
                        "{} 0 obj\n<<\n  /Type /Annot\n  /Subtype /Highlight\n  /Rect {}\n  /C {}\n  /QuadPoints {}\n  /CA {:.2}\n>>\nendobj\n",
                        obj_id, rect_str, color_str, quad_str, op.style.opacity * 0.5
                    );
                    dicts.push(dict);
                    obj_id += 1;
                }
                AnnotKind::Square(_sq) => {
                    let dict = format!(
                        "{} 0 obj\n<<\n  /Type /Annot\n  /Subtype /Square\n  /Rect {}\n  /C {}\n  /BS << /W {:.2} >>\n  /CA {:.2}\n>>\nendobj\n",
                        obj_id, rect_str, color_str, op.style.stroke_width, op.style.opacity
                    );
                    dicts.push(dict);
                    obj_id += 1;
                }
                AnnotKind::Circle(_) => {
                    let dict = format!(
                        "{} 0 obj\n<<\n  /Type /Annot\n  /Subtype /Circle\n  /Rect {}\n  /C {}\n  /BS << /W {:.2} >>\n  /CA {:.2}\n>>\nendobj\n",
                        obj_id, rect_str, color_str, op.style.stroke_width, op.style.opacity
                    );
                    dicts.push(dict);
                    obj_id += 1;
                }
                AnnotKind::Line { start, end } => {
                    let dict = format!(
                        "{} 0 obj\n<<\n  /Type /Annot\n  /Subtype /Line\n  /Rect {}\n  /L [{:.2} {:.2} {:.2} {:.2}]\n  /C {}\n  /BS << /W {:.2} >>\n>>\nendobj\n",
                        obj_id, rect_str, start[0], start[1], end[0], end[1], color_str, op.style.stroke_width
                    );
                    dicts.push(dict);
                    obj_id += 1;
                }
                AnnotKind::TextNote { content, .. } => {
                    let dict = format!(
                        "{} 0 obj\n<<\n  /Type /Annot\n  /Subtype /Text\n  /Rect {}\n  /C {}\n  /Contents ({})\n  /Open false\n>>\nendobj\n",
                        obj_id, rect_str, color_str, content.replace('(', "\\(").replace(')', "\\)")
                    );
                    dicts.push(dict);
                    obj_id += 1;
                }
                AnnotKind::Redaction(r) => {
                    let dict = format!(
                        "{} 0 obj\n<<\n  /Type /Annot\n  /Subtype /Redact\n  /Rect {}\n  /IC [0 0 0]\n  /OverlayText ({})\n>>\nendobj\n",
                        obj_id, rect_str, r.overlay_text
                    );
                    dicts.push(dict);
                    obj_id += 1;
                }
                AnnotKind::Signature(s) => {
                    let dict = format!(
                        "{} 0 obj\n<<\n  /Type /Annot\n  /Subtype /Widget\n  /FT /Sig\n  /Rect {}\n  /T (Signature_{})\n>>\nendobj\n",
                        obj_id, rect_str, s.id
                    );
                    dicts.push(dict);
                    obj_id += 1;
                }
                _ => {
                    // TODO: Implement ISO-32000 export for other Phase 2 tools
                }
            }
        }

        dicts
    }
}
