//! Cryptographic Hash-Chained Audit Log for Meridian Phase 5
//!
//! Conforms to Architecture Section 4.3:
//! Every redaction, digital signature, and form modification records an immutable
//! entry in a SHA-256 hash chain:
//! `H_n = SHA-256(H_{n-1} || timestamp || doc_id || action || actor || details)`

use serde::{Deserialize, Serialize};

/// Standard FIPS 180-4 SHA-256 implementation (zero external dependencies)
pub fn sha256_hex(data: &[u8]) -> String {
    let mut h: [u32; 8] = [
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
    ];

    let k: [u32; 64] = [
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    ];

    let bit_len = (data.len() as u64) * 8;
    let mut msg = data.to_vec();
    msg.push(0x80);
    while (msg.len() % 64) != 56 {
        msg.push(0x00);
    }
    msg.extend_from_slice(&bit_len.to_be_bytes());

    for chunk in msg.chunks(64) {
        let mut w = [0u32; 64];
        for i in 0..16 {
            w[i] = u32::from_be_bytes([chunk[i * 4], chunk[i * 4 + 1], chunk[i * 4 + 2], chunk[i * 4 + 3]]);
        }
        for i in 16..64 {
            let s0 = w[i - 15].rotate_right(7) ^ w[i - 15].rotate_right(18) ^ (w[i - 15] >> 3);
            let s1 = w[i - 2].rotate_right(17) ^ w[i - 2].rotate_right(19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16].wrapping_add(s0).wrapping_add(w[i - 7]).wrapping_add(s1);
        }

        let mut a = h[0];
        let mut b = h[1];
        let mut c = h[2];
        let mut d = h[3];
        let mut e = h[4];
        let mut f = h[5];
        let mut g = h[6];
        let mut h_val = h[7];

        for i in 0..64 {
            let s1 = e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25);
            let ch = (e & f) ^ ((!e) & g);
            let temp1 = h_val
                .wrapping_add(s1)
                .wrapping_add(ch)
                .wrapping_add(k[i])
                .wrapping_add(w[i]);
            let s0 = a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22);
            let maj = (a & b) ^ (a & c) ^ (b & c);
            let temp2 = s0.wrapping_add(maj);

            h_val = g;
            g = f;
            f = e;
            e = d.wrapping_add(temp1);
            d = c;
            c = b;
            b = a;
            a = temp1.wrapping_add(temp2);
        }

        h[0] = h[0].wrapping_add(a);
        h[1] = h[1].wrapping_add(b);
        h[2] = h[2].wrapping_add(c);
        h[3] = h[3].wrapping_add(d);
        h[4] = h[4].wrapping_add(e);
        h[5] = h[5].wrapping_add(f);
        h[6] = h[6].wrapping_add(g);
        h[7] = h[7].wrapping_add(h_val);
    }

    format!(
        "{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}{:08x}",
        h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7]
    )
}

/// An immutable, cryptographically verifiable record of a document operation
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct AuditEntry {
    pub entry_index: u64,
    pub timestamp: i64,
    pub doc_id: String,
    pub action: String,
    pub actor: String,
    pub details: String,
    pub prev_hash: String,
    pub hash: String,
}

impl AuditEntry {
    pub fn compute_hash(
        prev_hash: &str,
        timestamp: i64,
        doc_id: &str,
        action: &str,
        actor: &str,
        details: &str,
    ) -> String {
        let payload = format!("{prev_hash}:{timestamp}:{doc_id}:{action}:{actor}:{details}");
        sha256_hex(payload.as_bytes())
    }
}

/// In-memory & serialized hash-chained audit log
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct AuditLog {
    pub doc_id: String,
    pub entries: Vec<AuditEntry>,
}

impl AuditLog {
    pub fn new(doc_id: String) -> Self {
        let mut log = Self {
            doc_id: doc_id.clone(),
            entries: Vec::new(),
        };
        // Genesis block
        let now = 1772712000; // Epoch baseline
        let genesis_prev = "0000000000000000000000000000000000000000000000000000000000000000";
        let hash = AuditEntry::compute_hash(
            genesis_prev,
            now,
            &doc_id,
            "DOCUMENT_INITIALIZED",
            "SYSTEM",
            "Meridian Quantum Core Initialized",
        );
        log.entries.push(AuditEntry {
            entry_index: 0,
            timestamp: now,
            doc_id,
            action: "DOCUMENT_INITIALIZED".to_string(),
            actor: "SYSTEM".to_string(),
            details: "Meridian Quantum Core Initialized".to_string(),
            prev_hash: genesis_prev.to_string(),
            hash,
        });
        log
    }

    pub fn append(&mut self, action: &str, actor: &str, details: &str, timestamp: i64) -> &AuditEntry {
        let prev_hash = self
            .entries
            .last()
            .map(|e| e.hash.clone())
            .unwrap_or_else(|| "0000000000000000000000000000000000000000000000000000000000000000".to_string());
        let entry_index = self.entries.len() as u64;
        let hash = AuditEntry::compute_hash(&prev_hash, timestamp, &self.doc_id, action, actor, details);

        self.entries.push(AuditEntry {
            entry_index,
            timestamp,
            doc_id: self.doc_id.clone(),
            action: action.to_string(),
            actor: actor.to_string(),
            details: details.to_string(),
            prev_hash,
            hash,
        });

        self.entries.last().unwrap()
    }

    /// Validates the cryptographic integrity of the entire chain
    pub fn verify_integrity(&self) -> bool {
        for i in 1..self.entries.len() {
            let prev = &self.entries[i - 1];
            let curr = &self.entries[i];
            if curr.prev_hash != prev.hash {
                return false;
            }
            let expected_hash = AuditEntry::compute_hash(
                &curr.prev_hash,
                curr.timestamp,
                &curr.doc_id,
                &curr.action,
                &curr.actor,
                &curr.details,
            );
            if curr.hash != expected_hash {
                return false;
            }
        }
        true
    }
}
