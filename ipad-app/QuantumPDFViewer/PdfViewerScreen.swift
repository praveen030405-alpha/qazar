import SwiftUI
import PencilKit

struct PdfViewerScreen: View {
    let documentTitle: String
    let template: String
    
    @State private var canvasView = PKCanvasView()
    @State private var toolPicker = PKToolPicker()
    @State private var isAnnotationMode = false
    @State private var virtualPageCount = 1
    @State private var currentPage = 1
    
    let bgColor = Color(red: 12/255, green: 12/255, blue: 18/255)
    let rubyRed = Color(red: 239/255, green: 68/255, blue: 68/255)
    let paperColor = Color(red: 250/255, green: 249/255, blue: 246/255)
    
    var body: some View {
        ZStack {
            bgColor.ignoresSafeArea()
            
            VStack(spacing: 0) {
                
                // TOP Annotation Toolbar
                if isAnnotationMode {
                    HStack {
                        Button(action: {
                            isAnnotationMode = false
                            canvasView.drawingPolicy = .anyInput
                            toolPicker.setVisible(false, forFirstResponder: canvasView)
                            canvasView.resignFirstResponder()
                        }) {
                            Text("Done")
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(rubyRed)
                                .cornerRadius(8)
                        }
                        
                        Spacer()
                        
                        Text("Annotation Mode (Pencil Only)")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                        
                        Spacer()
                    }
                    .padding()
                    .background(Color.white.opacity(0.1))
                    .transition(.move(edge: .top))
                }
                
                // Native Notebook UI (Binder Style)
                HStack(spacing: 0) {
                    
                    // Binder Rings Overlay (Left Edge)
                    ZStack(alignment: .leading) {
                        // Notebook Paper Background
                        paperColor
                            .cornerRadius(12, corners: [.topRight, .bottomRight])
                            .shadow(color: .black.opacity(0.5), radius: 10, x: -5, y: 0)
                            .padding(.leading, 32)
                        
                        // Render PDF/PencilKit Canvas on top of the paper
                        PdfCanvasWrapper(canvasView: $canvasView, toolPicker: $toolPicker, isAnnotationMode: $isAnnotationMode)
                            .padding(.leading, 32)
                        
                        // Binder Rings Graphics
                        VStack(spacing: 40) {
                            ForEach(0..<8) { _ in
                                BinderRing()
                            }
                        }
                        .padding(.leading, 10)
                        
                        // "Endless Page" drag detector or simple Add Page button for the MVP
                        VStack {
                            Spacer()
                            HStack {
                                Spacer()
                                Button(action: {
                                    virtualPageCount += 1
                                    currentPage = virtualPageCount
                                    // Scroll PKCanvasView or append to internal representation
                                }) {
                                    HStack {
                                        Text("Page \(currentPage) of \(virtualPageCount)")
                                            .foregroundColor(.gray)
                                        Image(systemName: "chevron.down.circle.fill")
                                            .foregroundColor(rubyRed)
                                    }
                                    .padding()
                                    .background(Color.white)
                                    .cornerRadius(20)
                                    .shadow(radius: 5)
                                }
                                .padding(24)
                            }
                        }
                    }
                    
                    // Quick Access Side Tabs (Right Edge)
                    VStack(spacing: 2) {
                        SideTab(color: .red, title: "1")
                        SideTab(color: .blue, title: "2")
                        SideTab(color: .green, title: "3")
                        SideTab(color: .yellow, title: "4")
                        Spacer()
                    }
                    .padding(.top, 40)
                    .frame(width: 40)
                }
                .padding()
            }
        }
        .navigationTitle(documentTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: {
                    isAnnotationMode.toggle()
                    if isAnnotationMode {
                        // Crucial Palm Rejection requirement:
                        canvasView.drawingPolicy = .pencilOnly
                        toolPicker.setVisible(true, forFirstResponder: canvasView)
                        canvasView.becomeFirstResponder()
                    } else {
                        toolPicker.setVisible(false, forFirstResponder: canvasView)
                        canvasView.resignFirstResponder()
                    }
                }) {
                    Image(systemName: isAnnotationMode ? "pencil.slash" : "pencil")
                        .foregroundColor(rubyRed)
                }
            }
        }
    }
}

// Custom shapes for the Binder Ring aesthetic
struct BinderRing: View {
    var body: some View {
        HStack(spacing: 0) {
            // Hole punch
            Circle()
                .fill(Color(red: 12/255, green: 12/255, blue: 18/255))
                .frame(width: 16, height: 16)
                .shadow(color: .black.opacity(0.8), radius: 2, x: inset, y: inset)
            
            // Metal Ring
            RoundedRectangle(cornerRadius: 4)
                .fill(LinearGradient(gradient: Gradient(colors: [.gray, .white, .gray]), startPoint: .top, endPoint: .bottom))
                .frame(width: 40, height: 8)
                .offset(x: -8)
                .shadow(radius: 2)
        }
    }
    
    let inset: CGFloat = 1
}

// Side tab graphic
struct SideTab: View {
    let color: Color
    let title: String
    
    var body: some View {
        ZStack {
            path
                .fill(color)
                .frame(width: 40, height: 60)
            
            Text(title)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.white)
        }
    }
    
    var path: Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: 0))
        path.addLine(to: CGPoint(x: 30, y: 0))
        path.addQuadCurve(to: CGPoint(x: 40, y: 10), control: CGPoint(x: 40, y: 0))
        path.addLine(to: CGPoint(x: 40, y: 50))
        path.addQuadCurve(to: CGPoint(x: 30, y: 60), control: CGPoint(x: 40, y: 60))
        path.addLine(to: CGPoint(x: 0, y: 60))
        path.closeSubpath()
        return path
    }
}

// UIViewRepresentable to bridge PencilKit into SwiftUI
struct PdfCanvasWrapper: UIViewRepresentable {
    @Binding var canvasView: PKCanvasView
    @Binding var toolPicker: PKToolPicker
    @Binding var isAnnotationMode: Bool
    
    class Coordinator: NSObject, PKCanvasViewDelegate, PKToolPickerObserver {
        var parent: PdfCanvasWrapper
        var previousTool: PKTool?
        
        init(_ parent: PdfCanvasWrapper) {
            self.parent = parent
        }
        
        func toolPickerSelectedToolDidChange(_ toolPicker: PKToolPicker) {
            // If they select a non-eraser tool, remember it!
            if !(toolPicker.selectedTool is PKEraserTool) {
                previousTool = toolPicker.selectedTool
            }
        }
        
        func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) {
            // Once they finish a stroke, if they were using the eraser, revert to the previous tool
            if parent.toolPicker.selectedTool is PKEraserTool {
                if let prev = previousTool {
                    parent.toolPicker.selectedTool = prev
                }
            }
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    func makeUIView(context: Context) -> PKCanvasView {
        canvasView.delegate = context.coordinator
        toolPicker.addObserver(canvasView)
        toolPicker.addObserver(context.coordinator)
        
        canvasView.isOpaque = false
        canvasView.backgroundColor = .clear
        
        // Ensure scrolling works with fingers, drawing with pencil
        canvasView.drawingPolicy = isAnnotationMode ? .pencilOnly : .anyInput
        
        return canvasView
    }
    
    func updateUIView(_ uiView: PKCanvasView, context: Context) {
        uiView.drawingPolicy = isAnnotationMode ? .pencilOnly : .anyInput
    }
}

// Helper for specific corner radius
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape( RoundedCorner(radius: radius, corners: corners) )
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners
    
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}
