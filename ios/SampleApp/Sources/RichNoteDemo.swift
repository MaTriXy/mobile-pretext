import SwiftUI
import Pretext

// MARK: - Data Model

private enum FragmentKind { case text, chip }
private enum TextStyle { case body, link, code }
private enum ChipTone { case mention, status, priority, time, count }

private struct RichFragment {
    let kind: FragmentKind
    let text: String
    let style: TextStyle
    let tone: ChipTone?
}

private let noteFragments: [RichFragment] = [
    .init(kind: .text, text: "Ship ", style: .body, tone: nil),
    .init(kind: .chip, text: "@maya", style: .body, tone: .mention),
    .init(kind: .text, text: "'s ", style: .body, tone: nil),
    .init(kind: .text, text: "rich-note", style: .code, tone: nil),
    .init(kind: .text, text: " card once ", style: .body, tone: nil),
    .init(kind: .text, text: "pre-wrap", style: .code, tone: nil),
    .init(kind: .text, text: " lands. Status ", style: .body, tone: nil),
    .init(kind: .chip, text: "blocked", style: .body, tone: .status),
    .init(kind: .text, text: " by ", style: .body, tone: nil),
    .init(kind: .text, text: "vertical text", style: .link, tone: nil),
    .init(kind: .text, text: " research, but \u{5317}\u{4EAC} copy and Arabic QA are both green. Keep ", style: .body, tone: nil),
    .init(kind: .chip, text: "\u{062C}\u{0627}\u{0647}\u{0632}", style: .body, tone: .status),
    .init(kind: .text, text: " for ", style: .body, tone: nil),
    .init(kind: .text, text: "Cmd+K", style: .code, tone: nil),
    .init(kind: .text, text: " docs; the review bundle now includes \u{4E2D}\u{6587} labels, \u{0639}\u{0631}\u{0628}\u{064A} fallback, and one more launch pass for ", style: .body, tone: nil),
    .init(kind: .chip, text: "Fri 2:30 PM", style: .body, tone: .time),
    .init(kind: .text, text: ". Keep ", style: .body, tone: nil),
    .init(kind: .text, text: "layoutNextLine()", style: .code, tone: nil),
    .init(kind: .text, text: " public, tag this ", style: .body, tone: nil),
    .init(kind: .chip, text: "P1", style: .body, tone: .priority),
    .init(kind: .text, text: ", keep ", style: .body, tone: nil),
    .init(kind: .chip, text: "3 reviewers", style: .body, tone: .count),
    .init(kind: .text, text: ", and route feedback to ", style: .body, tone: nil),
    .init(kind: .text, text: "design sync", style: .link, tone: nil),
    .init(kind: .text, text: ".", style: .body, tone: nil),
]

// MARK: - Color Palette

private let panelBackground = Color(red: 1.0, green: 0.99, blue: 0.97)
private let inkColor = Color(red: 0.13, green: 0.11, blue: 0.09)
private let mutedColor = Color(red: 0.43, green: 0.39, blue: 0.36)
private let accentLink = Color(red: 0.58, green: 0.37, blue: 0.23)
private let ruleBorder = Color(red: 0.85, green: 0.81, blue: 0.76)

// MARK: - Chip Colors

private func chipBackground(_ tone: ChipTone) -> Color {
    switch tone {
    case .mention:  return Color(red: 0.87, green: 0.93, blue: 1.0)
    case .status:   return Color(red: 1.0, green: 0.95, blue: 0.83)
    case .priority: return Color(red: 1.0, green: 0.89, blue: 0.89)
    case .time:     return Color(red: 0.88, green: 0.97, blue: 0.89)
    case .count:    return Color(red: 0.92, green: 0.90, blue: 0.98)
    }
}

private func chipForeground(_ tone: ChipTone) -> Color {
    switch tone {
    case .mention:  return Color(red: 0.08, green: 0.35, blue: 0.53)
    case .status:   return Color(red: 0.57, green: 0.38, blue: 0.03)
    case .priority: return Color(red: 0.56, green: 0.14, blue: 0.14)
    case .time:     return Color(red: 0.21, green: 0.37, blue: 0.22)
    case .count:    return Color(red: 0.28, green: 0.24, blue: 0.51)
    }
}

// MARK: - Font Specs

private let bodyFontSpec = FontSpec(name: "Helvetica Neue", size: 17)
private let codeFontSpec = FontSpec(name: "Menlo", size: 14)
private let chipFontSpec = FontSpec(name: "Helvetica Neue", size: 12)

// MARK: - Layout Constants

private let lineHeight: Double = 34
private let chipHeight: Double = 24
private let inlineGap: Double = 4
private let codePadH: Double = 7
private let codePadV: Double = 2
private let codeCornerRadius: Double = 9
private let chipPadH: Double = 10
private let chipCornerRadius: Double = 12

// MARK: - Laid-out Element

private enum PlacedElementKind {
    case bodyText(String)
    case linkText(String)
    case codeText(String)
    case chip(String, ChipTone)
}

private struct PlacedElement {
    let kind: PlacedElementKind
    let x: Double
    let y: Double
    let width: Double
}

// MARK: - Layout Engine

private func layoutNote(fragments: [RichFragment], maxWidth: Double) -> (elements: [PlacedElement], totalHeight: Double) {
    var elements: [PlacedElement] = []
    var cursorX: Double = 0
    var cursorY: Double = 0

    func advanceToNextLine() {
        cursorX = 0
        cursorY += lineHeight
    }

    func remainingWidth() -> Double {
        max(0, maxWidth - cursorX)
    }

    for fragment in fragments {
        switch fragment.kind {
        case .chip:
            guard let tone = fragment.tone else { continue }
            let prepared = Pretext.prepareWithSegments(fragment.text, font: chipFontSpec)
            let result = Pretext.layoutWithLines(prepared, maxWidth: 10000, lineHeight: chipHeight)
            let textWidth = result.lines.first?.width ?? 0
            let chipWidth = textWidth + chipPadH * 2

            if cursorX > 0 && cursorX + inlineGap + chipWidth > maxWidth {
                advanceToNextLine()
            }
            if cursorX > 0 { cursorX += inlineGap }

            let chipY = cursorY + (lineHeight - chipHeight) / 2
            elements.append(PlacedElement(
                kind: .chip(fragment.text, tone),
                x: cursorX, y: chipY, width: chipWidth
            ))
            cursorX += chipWidth

        case .text:
            let font: FontSpec
            let elementMaker: (String, Double, Double, Double) -> PlacedElement

            switch fragment.style {
            case .body:
                font = bodyFontSpec
                elementMaker = { text, x, y, w in
                    PlacedElement(kind: .bodyText(text), x: x, y: y, width: w)
                }
            case .link:
                font = bodyFontSpec
                elementMaker = { text, x, y, w in
                    PlacedElement(kind: .linkText(text), x: x, y: y, width: w)
                }
            case .code:
                font = codeFontSpec
                elementMaker = { text, x, y, w in
                    PlacedElement(kind: .codeText(text), x: x, y: y, width: w)
                }
            }

            let isCode = fragment.style == .code
            let prepared = Pretext.prepareWithSegments(fragment.text, font: font)

            if isCode {
                // Code spans: measure as a single inline unit (no wrapping within)
                let result = Pretext.layoutWithLines(prepared, maxWidth: 10000, lineHeight: lineHeight)
                let textWidth = result.lines.first?.width ?? 0
                let totalWidth = textWidth + codePadH * 2

                if cursorX > 0 && cursorX + totalWidth > maxWidth {
                    advanceToNextLine()
                }

                elements.append(elementMaker(
                    fragment.text, cursorX, cursorY, totalWidth
                ))
                cursorX += totalWidth
            } else {
                // Body/link text: use layoutNextLine for wrapping
                var cursor = LayoutCursor(segmentIndex: 0, graphemeIndex: 0)

                while let line = Pretext.layoutNextLine(prepared, start: cursor, maxWidth: remainingWidth()) {
                    let text = line.text
                    let lineWidth = line.width

                    if !text.isEmpty {
                        elements.append(elementMaker(text, cursorX, cursorY, lineWidth))
                    }

                    cursor = line.end

                    // Check if there is more text remaining
                    let hasMore = Pretext.layoutNextLine(prepared, start: cursor, maxWidth: maxWidth) != nil
                    if hasMore {
                        cursorX = 0
                        cursorY += lineHeight
                    } else {
                        cursorX += lineWidth
                    }
                }
            }
        }
    }

    let totalHeight = cursorY + lineHeight
    return (elements, totalHeight)
}

// MARK: - Canvas Rendering

private func renderNote(context: inout GraphicsContext, elements: [PlacedElement], size: CGSize) {
    for element in elements {
        switch element.kind {
        case .bodyText(let text):
            let resolved = context.resolve(Text(text)
                .font(.custom("Helvetica Neue", size: 17))
                .foregroundColor(inkColor))
            context.draw(resolved, at: CGPoint(x: element.x, y: element.y + lineHeight * 0.72), anchor: .bottomLeading)

        case .linkText(let text):
            let resolved = context.resolve(Text(text)
                .font(.custom("Helvetica Neue", size: 17))
                .foregroundColor(accentLink)
                .underline())
            context.draw(resolved, at: CGPoint(x: element.x, y: element.y + lineHeight * 0.72), anchor: .bottomLeading)

        case .codeText(let text):
            // Draw code background
            let bgRect = CGRect(
                x: element.x,
                y: element.y + (lineHeight - 20) / 2,
                width: element.width,
                height: 20
            )
            let bgPath = RoundedRectangle(cornerRadius: codeCornerRadius)
                .path(in: bgRect)
            context.fill(bgPath, with: .color(Color(red: 0.93, green: 0.91, blue: 0.88)))

            // Draw code text
            let resolved = context.resolve(Text(text)
                .font(.custom("Menlo", size: 14))
                .foregroundColor(inkColor))
            context.draw(resolved, at: CGPoint(
                x: element.x + codePadH,
                y: element.y + lineHeight * 0.72
            ), anchor: .bottomLeading)

        case .chip(let text, let tone):
            // Draw pill background
            let pillRect = CGRect(
                x: element.x,
                y: element.y,
                width: element.width,
                height: chipHeight
            )
            let pillPath = RoundedRectangle(cornerRadius: chipCornerRadius)
                .path(in: pillRect)
            context.fill(pillPath, with: .color(chipBackground(tone)))

            // Draw chip text
            let resolved = context.resolve(Text(text)
                .font(.custom("Helvetica Neue", size: 12).bold())
                .foregroundColor(chipForeground(tone)))
            context.draw(resolved, at: CGPoint(
                x: element.x + chipPadH,
                y: element.y + chipHeight / 2
            ), anchor: .leading)
        }
    }
}

// MARK: - View

struct RichNoteDemo: View {
    @State private var widthRatio: Double = 0.85 // percentage of available width
    @State private var availableWidth: Double = 300

    private var maxWidth: Double { widthRatio * availableWidth }

    var body: some View {
        DemoSection(title: "Rich Text", subtitle: "Mixed inline elements with live reflow") {
            VStack(alignment: .leading, spacing: 12) {
                // Measure actual available width — updates on rotation
                GeometryReader { geo in
                    Color.clear.onAppear { availableWidth = geo.size.width }
                        .onChange(of: geo.size.width) { availableWidth = $0 }
                }
                .frame(height: 0)

                HStack {
                    Text("Width: \(Int(maxWidth))pt")
                        .font(.caption.monospaced())
                    Slider(value: $widthRatio, in: 0.3...1.0)
                }

                let innerPadH: Double = 16
                let innerPadV: Double = 12
                let layout = layoutNote(fragments: noteFragments, maxWidth: maxWidth - innerPadH * 2)

                Canvas { context, size in
                    // Draw panel background
                    let panelRect = CGRect(x: 0, y: 0, width: size.width, height: size.height)
                    let panelPath = RoundedRectangle(cornerRadius: 12)
                        .path(in: panelRect)
                    context.fill(panelPath, with: .color(panelBackground))

                    // Draw border
                    context.stroke(panelPath, with: .color(ruleBorder), lineWidth: 1)

                    // Offset for padding
                    var innerContext = context
                    innerContext.translateBy(x: innerPadH, y: innerPadV)

                    renderNote(context: &innerContext, elements: layout.elements, size: size)
                }
                .frame(width: maxWidth, height: layout.totalHeight + innerPadV * 2)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}
