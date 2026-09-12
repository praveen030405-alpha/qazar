use crate::{DGenome, repository::GenomeRepository};
use petgraph::graph::NodeIndex;
use std::collections::HashSet;

/// Represents the type of structural change between two D-Genomes.
#[derive(Debug, Clone)]
pub enum DiffChange {
    /// A node was added in the new genome.
    Added(NodeIndex),
    /// A node was removed from the old genome.
    Removed(NodeIndex),
    /// A node's content changed, but it serves the same structural role.
    Modified { old: NodeIndex, new: NodeIndex },
}

/// Computes the structural diff between two Document Genomes.
pub struct StructuralDiff;

impl StructuralDiff {
    /// Compares two genomes in the repository and returns a list of structural changes.
    pub fn compute_diff(repo: &GenomeRepository, old_genome: &DGenome, new_genome: &DGenome) -> Vec<DiffChange> {
        let mut changes = Vec::new();

        let old_root_idx = match old_genome.root {
            Some(idx) => idx,
            None => return changes,
        };
        let new_root_idx = match new_genome.root {
            Some(idx) => idx,
            None => return changes,
        };

        if repo.graph[old_root_idx].hash == repo.graph[new_root_idx].hash {
            return changes; // Perfect match
        }

        let old_hashes = Self::collect_hashes(repo, old_root_idx);
        let new_hashes = Self::collect_hashes(repo, new_root_idx);

        let removed: Vec<_> = old_hashes.difference(&new_hashes).collect();
        let added: Vec<_> = new_hashes.difference(&old_hashes).collect();

        for &hash in &removed {
            if let Some(&idx) = repo.hash_index.get(&hash) {
                changes.push(DiffChange::Removed(idx));
            }
        }

        for &hash in &added {
            if let Some(&idx) = repo.hash_index.get(&hash) {
                changes.push(DiffChange::Added(idx));
            }
        }

        changes
    }
    
    fn collect_hashes(repo: &GenomeRepository, start: NodeIndex) -> HashSet<blake3::Hash> {
        let mut hashes = HashSet::new();
        let mut visit = vec![start];
        
        while let Some(node) = visit.pop() {
            let hash = repo.graph[node].hash;
            if hashes.insert(hash) {
                // If newly inserted, visit children
                let neighbors = repo.graph.neighbors_directed(node, petgraph::Direction::Outgoing);
                visit.extend(neighbors);
            }
        }
        
        hashes
    }
}
