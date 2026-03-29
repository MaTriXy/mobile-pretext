import SwiftUI
import Pretext

struct LineBreakingDemo: View {
    let sampleText = "AGI \u{6625}\u{5929}\u{5230}\u{4E86}. \u{0628}\u{062F}\u{0623}\u{062A} \u{0627}\u{0644}\u{0631}\u{062D}\u{0644}\u{0629} \u{1F680} The future of AI is multilingual and beautiful."
    let font = FontSpec(name: "Helvetica Neue", size: 18)
    let lineHeight = 26.0
    @State private var maxWidth: Double = 280

    var body: some View {
        DemoSection(title: "Line Breaking", subtitle: "layoutWithLines() \u{2014} see each line") {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Width: \(Int(maxWidth))px")
                        .font(.caption.monospaced())
                    Slider(value: $maxWidth, in: 80...400)
                }

                let prepared = Pretext.prepareWithSegments(sampleText, font: font)
                let result = Pretext.layoutWithLines(prepared, maxWidth: maxWidth, lineHeight: lineHeight)

                // Actual rendered text at the constrained width
                Text(sampleText)
                    .font(.custom("Helvetica Neue", size: 18))
                    .lineSpacing(8)
                    .frame(width: maxWidth, alignment: .leading)
                    .padding(4)
                    .border(Color.cyan.opacity(0.4))

                Text("Pretext computed lines:")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                // Line-by-line breakdown
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(result.lines.enumerated()), id: \.offset) { index, line in
                        HStack(spacing: 8) {
                            Text("\(index + 1)")
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundStyle(.cyan)
                                .frame(width: 24)

                            Text(line.text)
                                .font(.custom("Helvetica Neue", size: 15))
                                .fixedSize(horizontal: true, vertical: false)

                            Spacer(minLength: 8)

                            Text("\(String(format: "%.0f", line.width))px")
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundStyle(.secondary.opacity(0.6))
                        }
                        .padding(.horizontal, 4)
                        .padding(.vertical, 4)
                        .background(index % 2 == 0 ? Color.clear : Color.white.opacity(0.03))
                    }
                }
                .padding(4)
                .border(Color.orange.opacity(0.3))

                Text("\(result.lineCount) lines \u{00B7} \(String(format: "%.0f", result.height))px total height")
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
            }
        }
    }
}
