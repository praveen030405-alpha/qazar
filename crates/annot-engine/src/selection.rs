use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct SelectionState {
    pub selected_op_ids: std::collections::HashSet<Uuid>,
}

pub struct SelectionEngine;

impl SelectionEngine {
    pub fn select_all() {
        // Placeholder
    }
}
