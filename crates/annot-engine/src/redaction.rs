//! True Redaction Module for Meridian Phase 5
//!
//! Conforms to Architecture Section 4.3:
//! "Redaction: true redaction, not black boxes. Mark -> resolve affected content ->
//! excise from content streams and resource dictionaries -> sanitize metadata ->
//! verify by re-extracting text and asserting absence -> recorded in hash-chained audit log."

use serde::{Deserialize, Serialize};
use crate::geometry::RectBox;

/// Standard classification reason for redaction per legal/compliance standards
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum RedactionReason {
    Pii,
    Financial,
    Privileged,
    TradeSecret,
    NationalSecurity,
    Custom(String),
}

impl RedactionReason {
    pub fn as_label(&self) -> String {
        match self {
            Self::Pii => "PII / PRIVACY".to_string(),
            Self::Financial => "FINANCIAL / CONFIDENTIAL".to_string(),
            Self::Privileged => "ATTORNEY-CLIENT PRIVILEGED".to_string(),
            Self::TradeSecret => "PROPRIETARY TRADE SECRET".to_string(),
            Self::NationalSecurity => "RESTRICTED / SECURITY".to_string(),
            Self::Custom(s) => s.clone(),
        }
    }
}

/// A marked or applied redaction record
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct RedactionRecord {
    pub id: String,
    pub page_index: u32,
    pub bounds: RectBox,
    pub reason: String,
    pub overlay_text: String,
    pub created_at: i64,
    pub actor: String,
    pub is_applied: bool,
    pub verified_text_absence: bool,
}

impl RedactionRecord {
    pub fn new(
        id: String,
        page_index: u32,
        bounds: RectBox,
        reason: String,
        overlay_text: String,
        created_at: i64,
        actor: String,
    ) -> Self {
        Self {
            id,
            page_index,
            bounds,
            reason,
            overlay_text,
            created_at,
            actor,
            is_applied: false,
            verified_text_absence: false,
        }
    }
}
