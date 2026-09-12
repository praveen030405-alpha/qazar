//! Tantivy inverted index management for document search.
//!
//! Provides high-throughput indexing and concurrent read searchers
//! capable of sub-100ms full-text retrieval across 10,000 pages.

use tantivy::{
    doc,
    schema::{Field, Schema, INDEXED, STORED, TEXT},
    Index, IndexReader, IndexWriter, ReloadPolicy,
};
use std::sync::Arc;
use parking_lot::RwLock;
use crate::error::Result;
use crate::extractor::ExtractedPage;

/// Field handles for the Tantivy schema.
#[derive(Clone, Copy)]
pub struct SearchSchemaFields {
    pub page: Field,
    pub body: Field,
    pub title: Field,
}

/// Tantivy search index wrapper.
pub struct TantivySearchIndex {
    #[allow(dead_code)]
    schema: Schema,
    fields: SearchSchemaFields,
    index: Index,
    writer: Arc<RwLock<IndexWriter>>,
    reader: IndexReader,
}

impl TantivySearchIndex {
    /// Create a new in-RAM Tantivy search index.
    pub fn create_in_ram() -> Result<Self> {
        let mut builder = Schema::builder();
        let page = builder.add_u64_field("page", INDEXED | STORED);
        let body = builder.add_text_field("body", TEXT | STORED);
        let title = builder.add_text_field("title", TEXT | STORED);
        let schema = builder.build();

        let index = Index::create_in_ram(schema.clone());
        // Allocate 30MB index writer buffer
        let writer = index.writer(30_000_000)?;
        let reader = index
            .reader_builder()
            .reload_policy(ReloadPolicy::OnCommitWithDelay)
            .try_into()?;

        let fields = SearchSchemaFields { page, body, title };

        Ok(Self {
            schema,
            fields,
            index,
            writer: Arc::new(RwLock::new(writer)),
            reader,
        })
    }

    /// Index a single extracted page.
    pub fn index_page(&self, page: &ExtractedPage, doc_title: &str) -> Result<()> {
        let writer = self.writer.write();
        writer.add_document(doc!(
            self.fields.page => page.page_index as u64,
            self.fields.body => page.full_text.as_str(),
            self.fields.title => doc_title,
        ))?;
        Ok(())
    }

    /// Batch index multiple pages in one go.
    pub fn batch_index_pages(&self, pages: &[ExtractedPage], doc_title: &str) -> Result<()> {
        let writer = self.writer.write();
        for p in pages {
            writer.add_document(doc!(
                self.fields.page => p.page_index as u64,
                self.fields.body => p.full_text.as_str(),
                self.fields.title => doc_title,
            ))?;
        }
        Ok(())
    }


    /// Commit staged changes so they become searchable immediately.
    pub fn commit(&self) -> Result<()> {
        let mut writer = self.writer.write();
        writer.commit()?;
        self.reader.reload()?;
        Ok(())
    }

    pub fn fields(&self) -> SearchSchemaFields {
        self.fields
    }

    pub fn index(&self) -> &Index {
        &self.index
    }

    pub fn reader(&self) -> &IndexReader {
        &self.reader
    }
}
