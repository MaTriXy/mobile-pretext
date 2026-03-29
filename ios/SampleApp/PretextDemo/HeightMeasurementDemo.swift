import SwiftUI
import Pretext

struct HeightMeasurementDemo: View {
    private let sampleText = "The quick brown fox jumps over the lazy dog. This paragraph demonstrates how Pretext can compute text height without triggering layout reflow. Try adjusting the width slider to see the height recalculate instantly."
    private let font = FontSpec(name: "Helvetica Neue", size: 16)
    private let lineHeight = 22.0

    @State private var containerWidth: Double = 300
    @State private var actualHeight: CGFloat = 0
    @State private var prepared: PreparedText?

    var body: some View {
        DemoSection(title: "Height Measurement", subtitle: "Compute paragraph height without layout") {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Width: \(Int(containerWidth))px")
                        .font(.caption.monospaced())
                    Slider(value: $containerWidth, in: 100...400)
                }

                let result = prepared.map { Pretext.layout($0, maxWidth: containerWidth, lineHeight: lineHeight) }

                HStack(spacing: 16) {
                    StatBadge(label: "Lines", value: "\(result?.lineCount ?? 0)")
                    StatBadge(label: "Height", value: String(format: "%.0f", result?.height ?? 0))
                    StatBadge(label: "Actual", value: String(format: "%.0f", actualHeight))
                }

                Text(sampleText)
                    .font(.custom("Helvetica Neue", size: 16))
                    .lineSpacing(22 - 16)
                    .frame(width: containerWidth, alignment: .leading)
                    .background(
                        GeometryReader { geo in
                            Color.clear
                                .onAppear { actualHeight = geo.size.height }
                                .onChange(of: containerWidth) {
                                    actualHeight = geo.size.height
                                }
                        }
                    )
                    .background(Color.blue.opacity(0.05))
                    .border(Color.blue.opacity(0.2))
            }
        }
        .onAppear {
            prepared = Pretext.prepare(sampleText, font: font)
        }
    }
}
