# iOS Platform Guide

## Installation

Add Mobile-Pretext as a Swift Package dependency:

```swift
// Package.swift
dependencies: [
    .package(url: "https://github.com/MaTriXy/mobile-pretext.git", from: "1.0.0")
]

// Target
.product(name: "Pretext", package: "mobile-pretext")
```

Or in Xcode: File → Add Package Dependencies → paste the repo URL.

## Platform-Specific Details

### Font Specification

iOS uses `FontSpec` instead of a CSS font string:

```swift
let font = FontSpec(name: "Helvetica Neue", size: 16)
```

The `name` must match a font available on the system. Use the PostScript name for precise matching:
- `"Helvetica Neue"` — system Helvetica
- `"Inter"` — if bundled in your app
- `".SFUI-Regular"` — San Francisco (not recommended — use the font descriptor API instead)

### Measurement Engine

iOS uses **CoreText** (`CTLine`) for text measurement. CoreText is Apple's low-level text rendering framework and gives consistent results between measurement and rendering.

Unlike the browser version, **no emoji width correction is needed** — CoreText measures emoji the same way it renders them.

### Word Segmentation

iOS uses **NLTokenizer** from the NaturalLanguage framework for word boundary detection. It handles:
- Thai, Lao, Khmer (dictionary-based, no spaces between words)
- CJK (character-level boundaries)
- Standard Latin word boundaries

### Thread Safety

The measurement cache and segmenter state are protected with `NSLock`. You can safely call `prepare()` and `layout()` from any thread. However, for best performance, prepare texts on a background queue and layout on the main thread.

## SwiftUI Integration

### Height-only measurement

```swift
struct MessageCell: View {
    let message: String
    @State private var prepared: PreparedText?

    let font = FontSpec(name: "Helvetica Neue", size: 16)

    var body: some View {
        GeometryReader { geo in
            let result = prepared.map {
                Pretext.layout($0, maxWidth: Double(geo.size.width), lineHeight: 22)
            }
            Text(message)
                .font(.custom("Helvetica Neue", size: 16))
                .frame(height: result?.height ?? 0)
        }
        .onAppear { prepared = Pretext.prepare(message, font: font) }
    }
}
```

### Custom Canvas rendering

```swift
Canvas { context, size in
    let result = Pretext.layoutWithLines(prepared, maxWidth: Double(size.width), lineHeight: 22)
    for (i, line) in result.lines.enumerated() {
        context.draw(
            Text(line.text).font(.custom("Helvetica Neue", size: 16)),
            at: CGPoint(x: 0, y: Double(i) * 22)
        )
    }
}
```

## DocC API Reference

The Swift library includes a DocC documentation catalog. To browse it in Xcode:

1. Open the package in Xcode
2. Product → Build Documentation
3. Browse under "Pretext" in the documentation navigator

Or generate a static site:
```sh
cd ios
swift package generate-documentation --output-path ../docs/.vitepress/dist/api/ios
```

## Sample App

The repo includes a full sample app with 5 demo sections:

```sh
open ios/SampleApp/PretextDemo.xcodeproj
```

Set your development team in Signing & Capabilities, select your iPhone, and run.
