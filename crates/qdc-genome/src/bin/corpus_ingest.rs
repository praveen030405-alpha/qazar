use clap::Parser;
use pdf_kernel::PdfEngine;
use qdc_genome::{
    ingest::IngestionPipeline,
    repository::GenomeRepository,
};
use rayon::prelude::*;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Instant;
use walkdir::WalkDir;

#[derive(Parser, Debug)]
#[command(version, about = "QDC Document Genome Corpus Ingester")]
struct Args {
    /// Path to the directory containing PDFs to ingest
    #[arg(required = true)]
    corpus_dir: PathBuf,
}

fn main() {
    let args = Args::parse();

    println!("Scanning directory {:?} for PDFs...", args.corpus_dir);
    
    let mut pdf_paths = Vec::new();
    for entry in WalkDir::new(&args.corpus_dir).into_iter().filter_map(|e| e.ok()) {
        if let Some(ext) = entry.path().extension() {
            if ext.to_string_lossy().to_lowercase() == "pdf" {
                pdf_paths.push(entry.path().to_path_buf());
            }
        }
    }

    let total_files = pdf_paths.len();
    if total_files == 0 {
        println!("No PDF files found in the specified directory.");
        return;
    }
    
    println!("Found {} PDF files. Starting ingestion...", total_files);

    // Global repository wrapped in a Mutex for cross-document deduplication across threads
    let repo = Arc::new(Mutex::new(GenomeRepository::new()));

    let start_time = Instant::now();

    let results: Vec<Option<petgraph::graph::NodeIndex>> = pdf_paths.par_iter().map(|path| {
        let repo_clone = Arc::clone(&repo);
        
        let engine = match PdfEngine::new(std::path::Path::new("bin")) {
            Ok(eng) => eng,
            Err(e) => {
                println!("Failed to load PDFium for thread: {:?}", e);
                return None;
            }
        };
        
        // Load document
        let doc_result = engine.open_file(path, None);
        if let Ok(doc) = doc_result {
            // Lock repo to ingest
            let mut repo_lock = repo_clone.lock().unwrap();
            
            match IngestionPipeline::ingest(&mut repo_lock, &doc) {
                Ok(genome) => {
                    println!("Successfully ingested: {:?}", path.file_name().unwrap());
                    genome.root
                },
                Err(e) => {
                    eprintln!("Error ingesting {:?}: {}", path.file_name().unwrap(), e);
                    None
                }
            }
        } else {
            eprintln!("Failed to open PDF: {:?}", path.file_name().unwrap());
            None
        }
    }).collect();

    let success_count = results.iter().filter(|x| x.is_some()).count();
    let mut unique_roots = std::collections::HashSet::new();
    for r in &results {
        if let Some(idx) = r {
            unique_roots.insert(*idx);
        }
    }

    let duration = start_time.elapsed();
    
    // Calculate Deduplication
    let final_repo = repo.lock().unwrap();
    let unique_nodes = final_repo.hash_index.len();
    let total_attempts = final_repo.total_insertions_attempted;
    
    let dedup_percentage = if total_attempts > 0 {
        100.0 * (1.0 - (unique_nodes as f64 / total_attempts as f64))
    } else {
        0.0
    };

    println!("\n========================================================");
    println!("                QDC GENOME PHASE 1 RESULTS              ");
    println!("========================================================");
    println!("Total Documents:      {}", total_files);
    println!("Successfully Ingested: {}", success_count);
    println!("Total Time:           {:.2?}", duration);
    println!("Total Nodes Parsed:   {}", total_attempts);
    println!("Unique Nodes In DAG:  {}", unique_nodes);
    println!("Unique Root Indices:  {}", unique_roots.len());
    println!("Cross-Doc Deduplication: {:.2}%", dedup_percentage);
    println!("========================================================");
}
