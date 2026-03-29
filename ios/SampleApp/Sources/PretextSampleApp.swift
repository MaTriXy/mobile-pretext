import SwiftUI
import Pretext

@main
struct PretextSampleApp: App {
    var body: some Scene {
        WindowGroup {
            DemoHub()
        }
    }
}

struct DemoHub: View {
    @State private var showBreaker = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    // App header
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Mobile-Pretext")
                            .font(.largeTitle.bold())
                        Text("Pure-arithmetic text measurement for iOS")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.bottom, 8)

                    // Demo cards
                    NavigationLink { HeightMeasurementDemo().navigationTitle("Height Measurement") } label: {
                        DemoCard(title: "Height Measurement", subtitle: "Compute paragraph height without layout")
                    }
                    NavigationLink { LineBreakingDemo().navigationTitle("Line Breaking") } label: {
                        DemoCard(title: "Line Breaking", subtitle: "layoutWithLines() — see each line")
                    }
                    NavigationLink { MultiLanguageDemo().navigationTitle("Multi-Language") } label: {
                        DemoCard(title: "Multi-Language", subtitle: "Hebrew, Arabic, CJK, Thai, Emoji, Bidi")
                    }
                    NavigationLink { VariableWidthDemo().navigationTitle("Variable Width") } label: {
                        DemoCard(title: "Variable Width", subtitle: "Text flowing around obstacles")
                    }
                    NavigationLink {
                        ScrollView { RichNoteDemo().padding() }.navigationTitle("Rich Note")
                    } label: {
                        DemoCard(title: "Rich Note", subtitle: "Mixed inline elements with live reflow")
                    }
                    NavigationLink { PerformanceBenchmark().navigationTitle("Performance Test") } label: {
                        DemoCard(title: "Performance Test", subtitle: "Live layout speed measurement")
                    }

                    // Breaker game (full screen)
                    Button { showBreaker = true } label: {
                        DemoCard(title: "Pretext Breaker", subtitle: "Text-based brick breaker game")
                    }
                    .buttonStyle(.plain)
                }
                .padding()
            }
            .navigationTitle("Pretext Demo")
            #if os(iOS)
            .fullScreenCover(isPresented: $showBreaker) {
                PretextBreakerView()
            }
            #else
            .sheet(isPresented: $showBreaker) {
                PretextBreakerView()
                    .frame(minWidth: 400, minHeight: 600)
            }
            #endif
        }
    }
}

struct DemoCard: View {
    let title: String
    let subtitle: String

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(.tertiary)
        }
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
