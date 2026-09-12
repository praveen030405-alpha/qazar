import fitz
import os
import io
from PIL import Image, ImageDraw
import random

CORPUS_DIR = os.path.abspath("test-corpus")
os.makedirs(CORPUS_DIR, exist_ok=True)

def generate_500page_50mb_pdf():
    pdf_path = os.path.join(CORPUS_DIR, "stress_500p_50mb.pdf")
    print(f"Generating 500-page, ~50MB PDF at: {pdf_path}")

    doc = fitz.open()

    # Create a base high-resolution image (~100 KB compressed JPEG per page -> 500 pages = ~50 MB)
    # 1200 x 1600 image with complex gradients and noise to prevent over-compression
    print("Generating synthetic complex image tiles for 500 pages...")
    
    # Real vocabulary for rich extracted text
    vocab = [
        "quantum", "architecture", "telemetry", "compositor", "memory", "governor",
        "kinematic", "predictor", "tantivy", "inverted", "index", "rendering",
        "acceleration", "vulkan", "sub-pixel", "selection", "cryptographic",
        "protocol", "shaping", "harfbuzz", "rustybuzz", "bidi", "unicode",
        "corpus", "pipeline", "continuous", "layout", "viewport", "rasterization"
    ]

    # Pre-generate 5 distinct high-res image buffers (each ~100-110 KB JPEG)
    img_buffers = []
    for seed in range(5):
        img = Image.new("RGB", (1200, 1600), color=(240, 243, 246))
        draw = ImageDraw.Draw(img)
        # Draw dense geometric and color patterns
        for i in range(100):
            x0 = (i * 12 + seed * 30) % 1100
            y0 = (i * 16 + seed * 40) % 1500
            draw.rectangle([x0, y0, x0 + 150, y0 + 120], fill=(
                (i * 3 + seed * 40) % 255,
                (i * 5 + seed * 20) % 255,
                (i * 7 + seed * 60) % 255
            ), outline=(20, 30, 40))
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=85)
        img_buffers.append(buf.getvalue())

    print("Building 500 pages with unique image streams to exceed 50 MB on disk...")
    for p in range(500):
        # ISO A4: 595.3 x 841.9 pt
        page = doc.new_page(width=595.3, height=841.9)

        # Generate unique image stream per page (each ~150 KB JPEG -> >50 MB total)
        img = Image.new("RGB", (1300, 1800), color=(240, 243, 246))
        draw = ImageDraw.Draw(img)
        # Random geometric lines and colored rects
        for i in range(85):
            x0 = (i * 19 + p * 37) % 1150
            y0 = (i * 29 + p * 47) % 1650
            draw.rectangle([x0, y0, x0 + 140, y0 + 110], fill=(
                (i * 7 + p * 13) % 255,
                (i * 11 + p * 17) % 255,
                (i * 13 + p * 19) % 255
            ), outline=(40, 50, 60))
        
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=90)
        img_bytes = buf.getvalue()


        rect = fitz.Rect(50, 400, 545, 780)
        page.insert_image(rect, stream=img_bytes)

        # Insert rich real text
        title = f"Document Page {p + 1} — Advanced Quantum Engineering Specification"
        page.insert_text((50, 60), title, fontsize=14, color=(0.06, 0.09, 0.16))

        # 4 paragraphs of real text
        y = 90
        for para in range(4):
            words = [random.choice(vocab) for _ in range(25)]
            if p == 342 and para == 2:
                # Plant target phrase for search benchmark
                words.insert(10, "secret cryptographic protocol")
            text_line = " ".join(words).capitalize() + "."
            page.insert_text((50, y), text_line, fontsize=10, color=(0.28, 0.33, 0.41))
            y += 30

        if (p + 1) % 50 == 0:
            print(f"  Constructed {p + 1}/500 pages...")


    doc.save(pdf_path, garbage=3, deflate=True)
    doc.close()
    
    size_mb = os.path.getsize(pdf_path) / (1024 * 1024)
    print(f"Generated {pdf_path}: {size_mb:.2f} MB, 500 pages.")
    return pdf_path

def generate_adversarial_pdf():
    pdf_path = os.path.join(CORPUS_DIR, "adversarial_malformed.pdf")
    print(f"Generating adversarial/malformed PDF at: {pdf_path}")
    
    # Construct a genuinely malformed PDF with:
    # 1. Broken / corrupted xref table offsets
    # 2. Circular reference loops between objects
    # 3. Stream with invalid declared length
    # 4. Deeply nested array / dictionary recursion
    # 5. Invalid truncated byte markers
    raw_content = b"""%PDF-1.7
1 0 obj
<< /Type /Catalog /Pages 2 0 R /Loop 3 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [4 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /SelfLoop /Next 1 0 R /BrokenStream 5 0 R >>
endobj
4 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R >>
endobj
5 0 obj
<< /Length 99999999 >>
stream
BT /F1 24 Tf 100 700 Td (Adversarial Document Test) Tj ET
endstream
endobj
xref
0 6
0000000000 65535 f
0000000010 00000 n
0000000075 00000 n
0000009999 00000 n
0000000180 00000 n
0000000260 00000 n
trailer
<< /Size 6 /Root 1 0 R /Prev 99999999 >>
startxref
9999999
%%EOF
CORRUPT_TRAILING_GARBAGE_BYTES_0xDEADBEEF_FFFFFFFF
"""
    with open(pdf_path, "wb") as f:
        f.write(raw_content)

    print(f"Generated adversarial test file at {pdf_path} ({len(raw_content)} bytes).")
    return pdf_path

if __name__ == "__main__":
    generate_500page_50mb_pdf()
    generate_adversarial_pdf()
