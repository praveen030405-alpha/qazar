pub mod ingest;
pub mod diff;
pub mod repository;

use blake3::Hash;
use petgraph::graph::NodeIndex;
use serde::{Deserialize, Serialize};

/// The types of nodes in the Document Genome Merkle-DAG.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum NodeType {
    Document { title: Option<String> },
    Page { index: u32 },
    Paragraph,
    Heading { level: u8 },
    Table,
    TableRow,
    TableCell,
    Figure,
    Footnote,
    Entity { label: String },
    TextChunk { content: String }, // Leaf node containing actual text
    ImageChunk { data_hash: Hash }, // Leaf node containing image reference
    Font { name: String }, // Leaf node for font deduplication
}

/// A node in the Document Genome.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DGenomeNode {
    pub node_type: NodeType,
    /// The BLAKE3 hash of this node's content and its children's hashes.
    pub hash: Hash,
    /// The bounding box on the page, if applicable.
    pub bounds: Option<[f32; 4]>,
}

/// The types of relationships (edges) between nodes.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub enum EdgeType {
    /// Hierarchical containment (e.g., Page contains Paragraph)
    Contains,
    /// Sequential reading order
    Next,
    /// Cross-reference (e.g., Table refers to Footnote)
    References,
    /// Semantic relationship (e.g., TextChunk belongs to Entity)
    Semantic,
}

/// The Document Genome: a handle to a specific document within the GenomeRepository.
#[derive(Debug, Clone)]
pub struct DGenome {
    /// The root node of the document in the repository graph.
    pub root: Option<NodeIndex>,
}

impl Default for DGenome {
    fn default() -> Self {
        Self::new()
    }
}

impl DGenome {
    pub fn new() -> Self {
        Self { root: None }
    }

    /// Computes the content-addressable hash for a new node.
    pub fn compute_hash(node_type: &NodeType, child_hashes: &[&blake3::Hash]) -> blake3::Hash {
        let mut hasher = blake3::Hasher::new();
        
        let node_bytes = bincode::serialize(node_type).expect("Failed to serialize node type");
        hasher.update(&node_bytes);

        for child_hash in child_hashes {
            hasher.update(child_hash.as_bytes());
        }

        hasher.finalize()
    }

    /// Sets the root node of the genome.
    pub fn set_root(&mut self, root: NodeIndex) {
        self.root = Some(root);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::repository::GenomeRepository;

    #[test]
    fn test_hash_identical_components() {
        let node1 = NodeType::TextChunk { content: "Hello World".to_string() };
        let node2 = NodeType::TextChunk { content: "Hello World".to_string() };
        
        let hash1 = DGenome::compute_hash(&node1, &[]);
        let hash2 = DGenome::compute_hash(&node2, &[]);
        
        assert_eq!(hash1, hash2, "Identical components should have the same hash");
    }

    #[test]
    fn test_hash_different_components() {
        let node1 = NodeType::TextChunk { content: "Hello World".to_string() };
        let node2 = NodeType::TextChunk { content: "Goodbye World".to_string() };
        
        let hash1 = DGenome::compute_hash(&node1, &[]);
        let hash2 = DGenome::compute_hash(&node2, &[]);
        
        assert_ne!(hash1, hash2, "Different components should have different hashes");
        
        // Also test references
        let parent1 = NodeType::Paragraph;
        let p_hash1 = DGenome::compute_hash(&parent1, &[&hash1]);
        let p_hash2 = DGenome::compute_hash(&parent1, &[&hash2]);
        
        assert_ne!(p_hash1, p_hash2, "Different children should yield different parent hashes");
    }

    #[test]
    fn test_ingest_duplicate_document() {
        let mut repo = GenomeRepository::new();
        
        let mut ingest_mock_doc = |repo: &mut GenomeRepository| {
            let chunk_hash = DGenome::compute_hash(&NodeType::TextChunk { content: "Repeated Text".to_string() }, &[]);
            let chunk_node = DGenomeNode {
                node_type: NodeType::TextChunk { content: "Repeated Text".to_string() },
                hash: chunk_hash,
                bounds: None,
            };
            
            let (leaf_idx, _) = repo.insert_node(chunk_node);
            
            let para_type = NodeType::Paragraph;
            let para_hash = DGenome::compute_hash(&para_type, &[&repo.graph[leaf_idx].hash]);
            let para_node = DGenomeNode {
                node_type: para_type,
                hash: para_hash,
                bounds: None,
            };
            
            let (para_idx, _) = repo.insert_node(para_node);
            repo.add_edge(para_idx, leaf_idx, EdgeType::Contains);
        };
        
        ingest_mock_doc(&mut repo);
        let nodes_after_first = repo.hash_index.len();
        
        ingest_mock_doc(&mut repo);
        let nodes_after_second = repo.hash_index.len();
        
        assert_eq!(nodes_after_first, nodes_after_second, "Ingesting the same components should not duplicate nodes");
        assert_eq!(repo.total_insertions_attempted, 4);
    }
}