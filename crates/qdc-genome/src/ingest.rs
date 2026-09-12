use pdf_kernel::PdfDoc;
use text_engine::extractor::{ExtractedPage, TextExtractor};

use crate::{DGenome, DGenomeNode, EdgeType, NodeType, repository::GenomeRepository};

/// Pipeline to ingest a PDF document and construct its DGenome representation.
pub struct IngestionPipeline;

impl IngestionPipeline {
    /// Ingests a document using its pdf_kernel handle and builds the DGenome in the repository.
    pub fn ingest(repo: &mut GenomeRepository, doc: &PdfDoc<'_>) -> Result<DGenome, String> {
        let extractor = TextExtractor::new();
        let page_count = doc.page_count();

        // 1. Prepare Document node type
        let doc_info = doc.info();
        let root_type = NodeType::Document { title: doc_info.title };

        let mut child_hashes = Vec::new();
        let mut page_indices = Vec::new();

        // 2. Process each page
        for page_index in 0..page_count {
            // Extract raw text from pdfium
            let raw_text = doc.extract_page_text(page_index)
                .map_err(|e| format!("Failed to extract text from page {}: {:?}", page_index, e))?;
            
            // Reconstruct layout (lines, words) using text-engine
            let extracted_page = extractor.extract_page(&raw_text);
            
            // Build genome sub-graph for this page
            let (page_idx, page_hash) = Self::ingest_page(repo, &extracted_page, page_index);
            
            page_indices.push(page_idx);
            child_hashes.push(page_hash);
        }

        // 3. Compute final root hash
        let refs: Vec<&blake3::Hash> = child_hashes.iter().collect();
        let root_hash = DGenome::compute_hash(&root_type, &refs);

        // 4. Create and insert root node
        let root_node = DGenomeNode {
            node_type: root_type,
            hash: root_hash,
            bounds: None,
        };
        let (root_idx, _) = repo.insert_node(root_node);

        // 5. Add edges to pages
        for page_idx in page_indices {
            repo.add_edge(root_idx, page_idx, EdgeType::Contains);
        }

        let mut genome = DGenome::new();
        genome.set_root(root_idx);

        Ok(genome)
    }

    /// Process a single page, generating Paragraph nodes from lines.
    fn ingest_page(
        repo: &mut GenomeRepository,
        page: &ExtractedPage,
        page_index: usize,
    ) -> (petgraph::graph::NodeIndex, blake3::Hash) {
        let mut page_child_hashes = Vec::new();
        let mut paragraph_indices = Vec::new();

        // Basic structural analysis: group lines into paragraphs
        // This is a naive implementation for Phase 1.
        let mut current_paragraph_lines = Vec::new();

        for line in &page.lines {
            // Determine if new paragraph based on vertical distance
            if let Some(last_line) = current_paragraph_lines.last() {
                let last: &text_engine::extractor::ExtractedLine = last_line;
                let v_dist = line.bounds.top - last.bounds.bottom;
                
                // If the distance between lines is larger than a threshold, start new paragraph
                // Note: PDF coordinate system (0,0) is bottom-left, so top > bottom.
                if v_dist.abs() > 10.0 {
                    let (para_idx, para_hash) = Self::build_paragraph(repo, &current_paragraph_lines);
                    paragraph_indices.push(para_idx);
                    page_child_hashes.push(para_hash);
                    current_paragraph_lines.clear();
                }
            }
            current_paragraph_lines.push(line.clone());
        }

        if !current_paragraph_lines.is_empty() {
            let (para_idx, para_hash) = Self::build_paragraph(repo, &current_paragraph_lines);
            paragraph_indices.push(para_idx);
            page_child_hashes.push(para_hash);
        }

        // Create Page node
        let page_type = NodeType::Page { index: page_index as u32 };
        let refs: Vec<&blake3::Hash> = page_child_hashes.iter().collect();
        let page_hash = DGenome::compute_hash(&page_type, &refs);

        let page_node = DGenomeNode {
            node_type: page_type,
            hash: page_hash,
            bounds: None, // Can calculate page bounds if needed
        };

        let (page_idx, _) = repo.insert_node(page_node);

        // Add edges
        for para_idx in paragraph_indices {
            repo.add_edge(page_idx, para_idx, EdgeType::Contains);
        }

        (page_idx, page_hash)
    }

    /// Build a paragraph node from a collection of text lines
    fn build_paragraph(
        repo: &mut GenomeRepository,
        lines: &[text_engine::extractor::ExtractedLine],
    ) -> (petgraph::graph::NodeIndex, blake3::Hash) {
        let mut para_text = String::new();
        let mut para_bounds = None;
        let mut leaf_hashes = Vec::new();
        let mut leaf_indices = Vec::new();

        for line in lines {
            if !para_text.is_empty() {
                para_text.push(' ');
            }
            para_text.push_str(&line.text);

            let q = line.bounds;
            if let Some(b) = &mut para_bounds {
                let current_b: &mut [f32; 4] = b;
                current_b[0] = current_b[0].min(q.left as f32); // left
                current_b[1] = current_b[1].max(q.top as f32);  // top
                current_b[2] = current_b[2].max(q.right as f32); // right
                current_b[3] = current_b[3].min(q.bottom as f32); // bottom
            } else {
                para_bounds = Some([q.left as f32, q.top as f32, q.right as f32, q.bottom as f32]);
            }

            // Create leaf node for this chunk of text
            let leaf_type = NodeType::TextChunk { content: line.text.clone() };
            let leaf_hash = DGenome::compute_hash(&leaf_type, &[]);
            let leaf_node = DGenomeNode {
                node_type: leaf_type,
                hash: leaf_hash,
                bounds: Some([q.left as f32, q.top as f32, q.right as f32, q.bottom as f32]),
            };
            
            let (leaf_idx, _) = repo.insert_node(leaf_node);
            leaf_hashes.push(leaf_hash);
            leaf_indices.push(leaf_idx);
        }

        let para_type = NodeType::Paragraph;
        let refs: Vec<&blake3::Hash> = leaf_hashes.iter().collect();
        let para_hash = DGenome::compute_hash(&para_type, &refs);

        let para_node = DGenomeNode {
            node_type: para_type,
            hash: para_hash,
            bounds: para_bounds,
        };

        let (para_idx, _) = repo.insert_node(para_node);

        // Add edges to leaves
        for leaf_idx in leaf_indices {
            repo.add_edge(para_idx, leaf_idx, EdgeType::Contains);
        }

        (para_idx, para_hash)
    }
}
