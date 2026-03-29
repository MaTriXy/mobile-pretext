# Getting Started with Pretext

Learn how to measure text height without triggering layout reflow.

## Overview

The core workflow is prepare-once, layout-many:

1. Call ``Pretext/prepare(_:font:options:)`` when text first appears
2. Call ``Pretext/layout(_:maxWidth:lineHeight:)`` on every resize

### Measure Height

```swift
let font = FontSpec(name: "Helvetica Neue", size: 16)
let prepared = Pretext.prepare(longParagraph, font: font)

// Call on every width change — sub-microsecond per call
let result = Pretext.layout(prepared, maxWidth: containerWidth, lineHeight: 22)
print("Height: \(result.height), Lines: \(result.lineCount)")
```

### Get Line Contents

Switch from `prepare` to `prepareWithSegments` for line-level data:

```swift
let prepared = Pretext.prepareWithSegments(text, font: font)
let result = Pretext.layoutWithLines(prepared, maxWidth: 300, lineHeight: 22)

for line in result.lines {
    print("\(line.text) — width: \(line.width)")
}
```

### Variable-Width Layout

Use `layoutNextLine` to flow text around obstacles:

```swift
var cursor = LayoutCursor(segmentIndex: 0, graphemeIndex: 0)
var y = 0.0

while let line = Pretext.layoutNextLine(prepared, start: cursor, maxWidth: widthAtY(y)) {
    renderLine(line, at: y)
    cursor = line.end
    y += lineHeight
}
```

## Language Support

Pretext handles all scripts: Hebrew, Arabic, Chinese, Japanese, Korean, Thai, Hindi, Myanmar, emoji, and mixed bidirectional text. No special configuration needed.
