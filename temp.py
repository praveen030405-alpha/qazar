with open('android-app/app/src/main/java/com/example/meridian/ui/viewer/PdfViewerScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()
paren_count = 0
for i, line in enumerate(lines[:730]):
    paren_count += line.count('(')
    paren_count -= line.count(')')
    if paren_count < 0:
        print(f'Negative paren depth at line {i+1}: {line.strip()}')
        paren_count = 0 # reset
