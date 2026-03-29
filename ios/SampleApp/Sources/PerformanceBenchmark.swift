import SwiftUI
import Pretext

private let corpusTexts = [
    "The quick brown fox jumps over the lazy dog.",
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor.",
    "AGI \u{6625}\u{5929}\u{5230}\u{4E86}. \u{0628}\u{062F}\u{0623}\u{062A} \u{0627}\u{0644}\u{0631}\u{062D}\u{0644}\u{0629} \u{1F680} The future is multilingual.",
    "Swift is a powerful programming language for iOS and macOS.",
    "\u{4F60}\u{597D}\u{4E16}\u{754C}\u{FF01}Hola Mundo! Bonjour le monde! \u{3053}\u{3093}\u{306B}\u{3061}\u{306F}\u{4E16}\u{754C}\u{FF01}",
    "\u{0645}\u{0631}\u{062D}\u{0628}\u{0627} \u{0628}\u{0627}\u{0644}\u{0639}\u{0627}\u{0644}\u{0645}! Hello World! \u{05E9}\u{05DC}\u{05D5}\u{05DD} \u{05E2}\u{05D5}\u{05DC}\u{05DD}!",
    "\u{D55C}\u{AD6D}\u{C5B4} \u{D14D}\u{C2A4}\u{D2B8} \u{B808}\u{C774}\u{C544}\u{C6C3} \u{D14C}\u{C2A4}\u{D2B8}\u{C785}\u{B2C8}\u{B2E4}.",
    "Text layout without triggering native layout passes, pure arithmetic.",
    "Pretext measures once with prepare(), then layouts instantly on resize.",
    "\u{30C6}\u{30AD}\u{30B9}\u{30C8}\u{306E}\u{30EC}\u{30A4}\u{30A2}\u{30A6}\u{30C8}\u{3092}\u{7D14}\u{7C8B}\u{306A}\u{7B97}\u{8853}\u{3067}\u{8A08}\u{7B97}\u{3002}",
    "Swift Package for iOS using CoreText and NLTokenizer.",
    "Mixed bidirectional text with Hebrew \u{05E2}\u{05D1}\u{05E8}\u{05D9}\u{05EA} and Arabic \u{0639}\u{0631}\u{0628}\u{064A} inline.",
]

private struct TextResult: Identifiable {
    let id: Int
    let text: String
    let lineCount: Int
    let height: Double
}

private struct RunResult {
    let textCount: Int
    let widthPt: Double
    let prepareMs: Double
    let layoutMs: Double
    let perTextUs: Double
    let totalLines: Int
    let results: [TextResult]
}

struct PerformanceBenchmark: View {
    @State private var textCount: Double = 100
    @State private var widthRatio: Double = 0.75
    @State private var availableWidth: Double = 300
    @State private var isRunning = false
    @State private var runResult: RunResult?

    let font = FontSpec(name: "Helvetica Neue", size: 14)
    let lineHeight = 20.0

    private var widthPt: Double { widthRatio * availableWidth }

    var body: some View {
        ScrollViewReader { scrollProxy in
        List {
            // Controls
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    EmptyView().id("controls-top")
                    Text("Performance Test")
                        .font(.headline)
                    Text("Set parameters, run, scroll through rendered texts, then see stats")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                HStack {
                    Text("Texts: \(Int(textCount))")
                        .font(.caption.monospaced())
                        .frame(width: 80, alignment: .leading)
                    Slider(value: $textCount, in: 10...500, step: 10)
                }

                HStack {
                    Text("Width: \(Int(widthPt))pt")
                        .font(.caption.monospaced())
                        .frame(width: 80, alignment: .leading)
                    Slider(value: $widthRatio, in: 0.3...1.0)
                }

                // Measure available width
                GeometryReader { geo in
                    Color.clear.onAppear { availableWidth = geo.size.width }
                        .onChange(of: geo.size.width) { availableWidth = $0 }
                }
                .frame(height: 0)

                Button {
                    runTest()
                } label: {
                    Text(isRunning ? "Running..." : "Run Test (\(Int(textCount)) texts at \(Int(widthPt))pt)")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(isRunning)
            }

            // Rendered texts
            if let result = runResult {
                Section("\(result.results.count) texts rendered at \(Int(result.widthPt))pt") {
                    // Anchor for "jump to results"
                    EmptyView().id("results-top")
                    ForEach(result.results) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("#\(item.id + 1)")
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundStyle(.cyan)
                                Spacer()
                                Text("\(item.lineCount) lines \u{00B7} \(Int(item.height))px")
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundStyle(.green)
                            }
                            Text(item.text)
                                .font(.custom("Helvetica Neue", size: 14))
                                .lineSpacing(4)
                                .frame(width: result.widthPt, alignment: .leading)
                                .padding(8)
                                .background(Color(.systemGray6))
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                        }
                    }
                }

                // Stats
                Section("Stats") {
                    EmptyView().id("stats-top")
                    ResultRow(label: "prepare()", value: String(format: "%.2fms", result.prepareMs), subtitle: "One-time cost", color: .orange)
                    ResultRow(label: "layout()", value: String(format: "%.3fms", result.layoutMs), subtitle: "Per resize (avg of 5)", color: .green)
                    ResultRow(label: "Per text", value: String(format: "%.2f\u{00B5}s", result.perTextUs), subtitle: "layout() cost", color: .cyan)
                    ResultRow(label: "Total lines", value: "\(result.totalLines)", subtitle: "Across all texts", color: .yellow)

                    let ratio = result.prepareMs / max(result.layoutMs, 0.001)
                    Text("layout() is \(Int(ratio))x faster than prepare(). prepare() runs once when text appears, layout() runs instantly on every width change.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .listStyle(.insetGrouped)
        .overlay(alignment: .bottom) {
            if runResult != nil {
                HStack(spacing: 12) {
                    Button {
                        withAnimation { scrollProxy.scrollTo("stats-top", anchor: .top) }
                    } label: {
                        Text("Show Results")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(.green)
                            .foregroundStyle(.black)
                            .clipShape(Capsule())
                    }
                    Button {
                        withAnimation { scrollProxy.scrollTo("controls-top", anchor: .top) }
                    } label: {
                        Text("Jump to Top")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(.cyan)
                            .foregroundStyle(.black)
                            .clipShape(Capsule())
                    }
                }
                .padding(.bottom, 24)
                .shadow(radius: 8)
            }
        }
        } // ScrollViewReader
    }

    func runTest() {
        isRunning = true
        let count = Int(textCount)
        let width = widthPt
        let texts = (0..<count).map { corpusTexts[$0 % corpusTexts.count] }

        DispatchQueue.global(qos: .userInitiated).async {
            Pretext.clearCache()
            let t0 = CFAbsoluteTimeGetCurrent()
            let prepared = texts.map { Pretext.prepare($0, font: font) }
            let t1 = CFAbsoluteTimeGetCurrent()
            let prepareMs = (t1 - t0) * 1000

            let iterations = 5
            let t2 = CFAbsoluteTimeGetCurrent()
            var lastResults: [LayoutResult] = []
            for _ in 0..<iterations {
                lastResults = prepared.map { Pretext.layout($0, maxWidth: width, lineHeight: lineHeight) }
            }
            let t3 = CFAbsoluteTimeGetCurrent()
            let layoutMs = (t3 - t2) * 1000 / Double(iterations)
            let perTextUs = layoutMs * 1000 / Double(count)
            let totalLines = lastResults.reduce(0) { $0 + $1.lineCount }

            let results = texts.enumerated().map { (i, text) in
                TextResult(id: i, text: text, lineCount: lastResults[i].lineCount, height: lastResults[i].height)
            }

            DispatchQueue.main.async {
                runResult = RunResult(
                    textCount: count, widthPt: width,
                    prepareMs: prepareMs, layoutMs: layoutMs,
                    perTextUs: perTextUs, totalLines: totalLines,
                    results: results
                )
                isRunning = false
            }
        }
    }
}

private struct ResultRow: View {
    let label: String
    let value: String
    let subtitle: String
    let color: Color

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.subheadline.bold())
                Text(subtitle).font(.caption2).foregroundStyle(.secondary)
            }
            Spacer()
            Text(value)
                .font(.system(size: 18, weight: .bold, design: .monospaced))
                .foregroundStyle(color)
        }
    }
}
