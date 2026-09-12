import SwiftUI

@main
struct QuantumPDFViewerApp: App {
    var body: some Scene {
        WindowGroup {
            HomeScreen()
                .preferredColorScheme(.dark)
        }
    }
}
