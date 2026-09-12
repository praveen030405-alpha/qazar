use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Layer {
    pub id: Uuid,
    pub name: String,
    pub z_index: i32,
    pub visible: bool,
    pub opacity: f32,
    pub locked: bool,
    pub blend_mode: String,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct LayerLog {
    pub layers: std::collections::HashMap<Uuid, Layer>,
}

impl LayerLog {
    pub fn new() -> Self {
        Self::default()
    }
}
