import SwiftUI
import Pretext

struct VariableWidthDemo: View {
    let sampleText = "This text flows around an obstacle, just like CSS float. The first few lines are narrower because they share space with the blue rectangle. Once past it, lines expand to the full container width. This demonstrates layoutNextLine() with variable widths per line."
    let font = FontSpec(name: "Helvetica Neue", size: 15)
    let lineHeight = 21.0
    let containerWidth = 320.0
    let obstacleWidth = 100.0
    let obstacleHeight = 80.0

    var body: some View {
        DemoSection(title: "Variable Width", subtitle: "layoutNextLine() \u{2014} text around obstacles") {
            let lines = buildLines()

            ZStack(alignment: .topTrailing) {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.blue.opacity(0.15))
                    .frame(width: obstacleWidth, height: obstacleHeight)
                    .overlay(
                        Text("Obstacle")
                            .font(.caption2)
                            .foregroundStyle(.blue)
                    )

                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(lines.enumerated()), id: \.offset) { _, item in
                        Text(item.line.text)
                            .font(.custom("Helvetica Neue", size: 15))
                            .frame(width: item.availableWidth, alignment: .leading)
                            .frame(height: lineHeight)
                    }
                }
            }
            .frame(width: containerWidth, alignment: .topLeading)
            .border(Color.purple.opacity(0.2))
        }
    }

    private func buildLines() -> [(line: LayoutLine, availableWidth: Double)] {
        let prepared = Pretext.prepareWithSegments(sampleText, font: font)
        var result: [(line: LayoutLine, availableWidth: Double)] = []
        var cursor = LayoutCursor(segmentIndex: 0, graphemeIndex: 0)
        var y = 0.0

        while let line = Pretext.layoutNextLine(
            prepared,
            start: cursor,
            maxWidth: y < obstacleHeight ? containerWidth - obstacleWidth - 12 : containerWidth
        ) {
            let availableWidth = y < obstacleHeight ? containerWidth - obstacleWidth - 12 : containerWidth
            result.append((line: line, availableWidth: availableWidth))
            cursor = line.end
            y += lineHeight
        }

        return result
    }
}
