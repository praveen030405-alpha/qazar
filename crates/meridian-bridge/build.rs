fn main() {
    uniffi::generate_scaffolding("src/meridian.udl").unwrap_or_else(|e| {
        println!("cargo:warning=uniffi generate scaffolding failed: {}", e);
    });
}
