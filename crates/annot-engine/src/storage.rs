use rusqlite::{params, Connection, Result as SqlResult};
use uuid::Uuid;
use crate::{AnnotKind, AnnotOp, AnnotStyle, OpId};

/// SQLite embedded ACID persistence manager for the append-only annotation op-log
pub struct SqliteOpLogStore {
    conn: Connection,
}

impl SqliteOpLogStore {
    /// Opens or creates an in-memory or on-disk SQLite op-log database
    pub fn open_in_memory() -> SqlResult<Self> {
        let conn = Connection::open_in_memory()?;
        let store = Self { conn };
        store.init_schema()?;
        Ok(store)
    }

    pub fn open<P: AsRef<std::path::Path>>(path: P) -> SqlResult<Self> {
        let conn = Connection::open(path)?;
        // Enable WAL mode & synchronous = NORMAL for optimal write throughput
        conn.execute_batch(
            "PRAGMA journal_mode = WAL;
             PRAGMA synchronous = NORMAL;
             PRAGMA foreign_keys = ON;"
        )?;
        let store = Self { conn };
        store.init_schema()?;
        Ok(store)
    }

    fn init_schema(&self) -> SqlResult<()> {
        self.conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS annot_ops (
                actor_id TEXT NOT NULL,
                counter INTEGER NOT NULL,
                doc_id TEXT NOT NULL,
                page_index INTEGER NOT NULL,
                layer_id TEXT,
                group_id TEXT,
                kind_json TEXT NOT NULL,
                style_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                vclock INTEGER NOT NULL,
                tombstone INTEGER NOT NULL,
                target_actor_id TEXT,
                target_counter INTEGER,
                PRIMARY KEY (actor_id, counter)
            );
            CREATE INDEX IF NOT EXISTS idx_annot_doc_page ON annot_ops (doc_id, page_index);"
        )
    }

    /// Inserts a single annotation operation
    pub fn insert_op(&mut self, op: &AnnotOp) -> SqlResult<()> {
        let kind_json = serde_json::to_string(&op.kind).unwrap_or_default();
        let style_json = serde_json::to_string(&op.style).unwrap_or_default();
        let target_actor = op.target_op_id.map(|id| id.actor_id.to_string());
        let target_counter = op.target_op_id.map(|id| id.counter);

        let layer_id_str = op.layer_id.map(|id| id.to_string());
        let group_id_str = op.group_id.map(|id| id.to_string());

        self.conn.execute(
            "INSERT INTO annot_ops (actor_id, counter, doc_id, page_index, layer_id, group_id, kind_json, style_json, created_at, vclock, tombstone, target_actor_id, target_counter)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)",
            params![
                op.op_id.actor_id.to_string(),
                op.op_id.counter,
                op.doc_id,
                op.page_index,
                layer_id_str,
                group_id_str,
                kind_json,
                style_json,
                op.created_at,
                op.vclock,
                if op.tombstone { 1 } else { 0 },
                target_actor,
                target_counter,
            ],
        )?;
        Ok(())
    }

    /// High-performance transactional batch insert for high-throughput streaming
    pub fn insert_batch(&mut self, ops: &[AnnotOp]) -> SqlResult<()> {
        let tx = self.conn.transaction()?;
        {
            let mut stmt = tx.prepare(
                "INSERT INTO annot_ops (actor_id, counter, doc_id, page_index, layer_id, group_id, kind_json, style_json, created_at, vclock, tombstone, target_actor_id, target_counter)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)"
            )?;

            for op in ops {
                let kind_json = serde_json::to_string(&op.kind).unwrap_or_default();
                let style_json = serde_json::to_string(&op.style).unwrap_or_default();
                let target_actor = op.target_op_id.map(|id| id.actor_id.to_string());
                let target_counter = op.target_op_id.map(|id| id.counter);

                let layer_id_str = op.layer_id.map(|id| id.to_string());
                let group_id_str = op.group_id.map(|id| id.to_string());

                stmt.execute(params![
                    op.op_id.actor_id.to_string(),
                    op.op_id.counter,
                    op.doc_id,
                    op.page_index,
                    layer_id_str,
                    group_id_str,
                    kind_json,
                    style_json,
                    op.created_at,
                    op.vclock,
                    if op.tombstone { 1 } else { 0 },
                    target_actor,
                    target_counter,
                ])?;
            }
        }
        tx.commit()?;
        Ok(())
    }

    /// Loads all operations for a specific document
    pub fn load_doc_ops(&self, doc_id: &str) -> SqlResult<Vec<AnnotOp>> {
        let mut stmt = self.conn.prepare(
            "SELECT actor_id, counter, doc_id, page_index, layer_id, group_id, kind_json, style_json, created_at, vclock, tombstone, target_actor_id, target_counter
             FROM annot_ops
             WHERE doc_id = ?1
             ORDER BY vclock ASC, counter ASC"
        )?;

        let op_iter = stmt.query_map(params![doc_id], |row| {
            let actor_id_str: String = row.get(0)?;
            let counter: u64 = row.get(1)?;
            let doc_id: String = row.get(2)?;
            let page_index: u32 = row.get(3)?;
            let layer_id_str: Option<String> = row.get(4)?;
            let group_id_str: Option<String> = row.get(5)?;
            let kind_json: String = row.get(6)?;
            let style_json: String = row.get(7)?;
            let created_at: i64 = row.get(8)?;
            let vclock: u64 = row.get(9)?;
            let tombstone_int: i32 = row.get(10)?;
            let target_actor_str: Option<String> = row.get(11)?;
            let target_counter: Option<u64> = row.get(12)?;

            let actor_id = Uuid::parse_str(&actor_id_str).unwrap_or_default();
            let layer_id = layer_id_str.and_then(|s| Uuid::parse_str(&s).ok());
            let group_id = group_id_str.and_then(|s| Uuid::parse_str(&s).ok());
            let kind: AnnotKind = serde_json::from_str(&kind_json).unwrap_or(AnnotKind::Square(crate::RectBox::new(0.0, 0.0, 0.0, 0.0)));
            let style: AnnotStyle = serde_json::from_str(&style_json).unwrap_or_default();

            let target_op_id = match (target_actor_str, target_counter) {
                (Some(s), Some(c)) => Uuid::parse_str(&s).ok().map(|aid| OpId::new(aid, c)),
                _ => None,
            };

            Ok(AnnotOp {
                op_id: OpId::new(actor_id, counter),
                doc_id,
                page_index,
                layer_id,
                group_id,
                kind,
                style,
                created_at,
                vclock,
                tombstone: tombstone_int != 0,
                target_op_id,
            })
        })?;

        let mut ops = Vec::new();
        for op in op_iter {
            ops.push(op?);
        }
        Ok(ops)
    }

    /// Counts total operations in database
    pub fn count_ops(&self) -> SqlResult<usize> {
        let count: usize = self.conn.query_row("SELECT COUNT(*) FROM annot_ops", [], |r| r.get(0))?;
        Ok(count)
    }
}
