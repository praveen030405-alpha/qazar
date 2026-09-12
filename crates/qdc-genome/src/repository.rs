use petgraph::graph::{DiGraph, NodeIndex};
use std::collections::HashMap;
use blake3::Hash;

use crate::{DGenomeNode, EdgeType};

/// A global repository that stores the unified Document Genome graph across multiple documents.
/// This enables cross-document deduplication.
#[derive(Debug, Clone)]
pub struct GenomeRepository {
    /// The unified directed graph containing nodes from all ingested documents.
    pub graph: DiGraph<DGenomeNode, EdgeType>,
    /// Global map from node hash to graph index for fast deduplication.
    pub hash_index: HashMap<Hash, NodeIndex>,
    /// Tracks total attempted insertions across all documents.
    pub total_insertions_attempted: usize,
}

impl Default for GenomeRepository {
    fn default() -> Self {
        Self::new()
    }
}

impl GenomeRepository {
    pub fn new() -> Self {
        Self {
            graph: DiGraph::new(),
            hash_index: HashMap::new(),
            total_insertions_attempted: 0,
        }
    }

    /// Adds a new node to the repository, deduplicating if it already exists.
    /// Returns the NodeIndex and a boolean indicating if it was newly inserted.
    pub fn insert_node(&mut self, node: DGenomeNode) -> (NodeIndex, bool) {
        self.total_insertions_attempted += 1;
        
        if let Some(&idx) = self.hash_index.get(&node.hash) {
            return (idx, false); // Already exists (Cross-document deduplication!)
        }

        let hash = node.hash;
        let idx = self.graph.add_node(node);
        self.hash_index.insert(hash, idx);
        
        (idx, true)
    }

    /// Adds a directed edge between two nodes in the repository.
    pub fn add_edge(&mut self, parent: NodeIndex, child: NodeIndex, edge_type: EdgeType) {
        self.graph.add_edge(parent, child, edge_type);
    }
}
