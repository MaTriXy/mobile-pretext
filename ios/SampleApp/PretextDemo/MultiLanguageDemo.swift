import SwiftUI
import Pretext

struct MultiLanguageDemo: View {
    private let font = FontSpec(name: "Helvetica Neue", size: 16)
    private let lineHeight = 22.0
    private let maxWidth = 280.0

    private let samples: [(String, String)] = [
        ("English", "The quick brown fox jumps over the lazy dog."),
        ("CJK Mixed", "\u{4F60}\u{597D}\u{4E16}\u{754C}\u{FF01}Hello \u{6625}\u{5929}\u{5230}\u{4E86}\u{3002}\u{685C}\u{304C}\u{54B2}\u{3044}\u{3066}\u{3044}\u{308B}\u{3002}"),
        ("Arabic", "\u{0645}\u{0631}\u{062D}\u{0628}\u{0627} \u{0628}\u{0627}\u{0644}\u{0639}\u{0627}\u{0644}\u{0645}! \u{0647}\u{0630}\u{0627} \u{0646}\u{0635} \u{0639}\u{0631}\u{0628}\u{064A} \u{0644}\u{0644}\u{0627}\u{062E}\u{062A}\u{0628}\u{0627}\u{0631}"),
        ("Emoji", "Hello \u{1F30D}\u{1F30E}\u{1F30F}! The future is \u{1F680}\u{1F916}\u{1F4A1} and \u{1F3A8}\u{2728}"),
        ("Korean", "\u{D55C}\u{AD6D}\u{C5B4} \u{D14D}\u{C2A4}\u{D2B8} \u{B808}\u{C774}\u{C544}\u{C6C3} \u{D14C}\u{C2A4}\u{D2B8}\u{C785}\u{B2C8}\u{B2E4}. \u{C798} \u{C791}\u{B3D9}\u{D558}\u{B098}\u{C694}?"),
        ("Thai", "\u{0E2A}\u{0E27}\u{0E31}\u{0E2A}\u{0E14}\u{0E35}\u{0E04}\u{0E23}\u{0E31}\u{0E1A} \u{0E19}\u{0E35}\u{0E48}\u{0E04}\u{0E37}\u{0E2D}\u{0E01}\u{0E32}\u{0E23}\u{0E17}\u{0E14}\u{0E2A}\u{0E2D}\u{0E1A}\u{0E20}\u{0E32}\u{0E29}\u{0E32}\u{0E44}\u{0E17}\u{0E22}"),
        ("Mixed Bidi", "Hello \u{0645}\u{0631}\u{062D}\u{0628}\u{0627} World \u{0639}\u{0627}\u{0644}\u{0645} 123 \u{0664}\u{0665}\u{0666}"),
        ("Soft Hyphens", "Sup\u{00AD}er\u{00AD}cal\u{00AD}i\u{00AD}frag\u{00AD}il\u{00AD}is\u{00AD}tic is a long word"),
    ]

    @State private var preparedSamples: [(String, String, PreparedText)] = []

    var body: some View {
        DemoSection(title: "Multi-Language", subtitle: "Hebrew, Arabic, Chinese, Japanese, Korean, Thai, Emoji, Bidi") {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(Array(preparedSamples.enumerated()), id: \.offset) { index, item in
                    let (label, text, prepared) = item
                    let result = Pretext.layout(prepared, maxWidth: maxWidth, lineHeight: lineHeight)

                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(label)
                                .font(.caption.bold())
                            Spacer()
                            Text("\(result.lineCount)L / \(String(format: "%.0f", result.height))px")
                                .font(.caption.monospaced())
                                .foregroundStyle(.secondary)
                        }

                        Text(text)
                            .font(.custom("Helvetica Neue", size: 16))
                            .frame(width: maxWidth, alignment: .leading)
                            .lineSpacing(6)
                    }
                    .padding(.vertical, 4)

                    if index < preparedSamples.count - 1 {
                        Divider()
                    }
                }
            }
        }
        .onAppear {
            preparedSamples = samples.map { (label, text) in
                (label, text, Pretext.prepare(text, font: font))
            }
        }
    }
}
