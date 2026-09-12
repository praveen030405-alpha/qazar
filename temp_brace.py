with open('android-app/app/src/main/java/com/example/meridian/ui/viewer/PdfViewerScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

depth = 0
for i, line in enumerate(lines):
    opens = line.count('{')
    closes = line.count('}')
    depth += opens - closes
    if i in range(583, 600) or i in range(605, 612) or i in range(664, 672) or i in range(718, 726) or i in range(766, 775) or i in range(993, 1000):
        print(f'{i+1:04d} [depth={depth:2d}]: {line.rstrip()}')
