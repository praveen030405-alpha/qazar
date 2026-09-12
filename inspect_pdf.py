import os
import time
from pypdf import PdfReader

pdf_path = r"C:\Users\Praveen Kumar\Downloads\G1-Model-Test-Papers.pdf"
print(f"Inspecting: {pdf_path}")
print(f"File size: {os.path.getsize(pdf_path):,} bytes")

t0 = time.perf_counter()
reader = PdfReader(pdf_path)
t_open = time.perf_counter() - t0

num_pages = len(reader.pages)
print(f"Opened in {t_open*1000:.2f} ms")
print(f"Total Pages: {num_pages}")

# Sample dimensions and elements
dims = set()
total_images = 0
total_text_chars = 0
sample_pages = [0, 1, 10, 50, 100, 200, 300, 400, 500, 600, num_pages - 1]

for idx in sample_pages:
    if idx < num_pages:
        page = reader.pages[idx]
        box = page.mediabox
        dims.add((float(box.width), float(box.height)))
        txt = page.extract_text() or ""
        total_text_chars += len(txt)
        images = page.images
        total_images += len(images)

print(f"Unique Sample Dimensions (pt): {dims}")
print(f"Sample Pages Text density avg: {total_text_chars / len(sample_pages):.1f} chars/page")
print(f"Sample Pages Images: {total_images} images across sample pages")

# Check outlines / bookmarks
try:
    outline = reader.outline
    print(f"Bookmarks count: {len(outline) if outline else 0}")
except Exception as e:
    print(f"Outline error: {e}")

# Check metadata
meta = reader.metadata
if meta:
    print(f"Title: {meta.title}")
    print(f"Author: {meta.author}")
    print(f"Producer: {meta.producer}")
