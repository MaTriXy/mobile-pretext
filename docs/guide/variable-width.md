# Variable-Width Layout

`layoutNextLine()` is the iterator-style API for flowing text where the available width changes from line to line — like text wrapping around a floated image.

## How It Works

Instead of computing all lines at once, you step through one line at a time, providing a different `maxWidth` for each:

::: code-group

```swift [iOS]
let prepared = Pretext.prepareWithSegments(text, font: font)
var cursor = LayoutCursor(segmentIndex: 0, graphemeIndex: 0)
var y = 0.0

while let line = Pretext.layoutNextLine(prepared, start: cursor, maxWidth: widthAtY(y)) {
    // Render this line
    context.draw(Text(line.text), at: CGPoint(x: 0, y: y))

    // Advance to the next line
    cursor = line.end
    y += lineHeight
}
```

```kotlin [Android]
val prepared = Pretext.prepareWithSegments(text, measurer)
var cursor = LayoutCursor(0, 0)
var y = 0f

while (true) {
    val line = Pretext.layoutNextLine(prepared, cursor, widthAtY(y)) ?: break
    canvas.drawText(line.text, 0f, y + baseline, paint)
    cursor = line.end
    y += lineHeight
}
```

:::

## Text Around an Image

The classic use case — an image floated to the right, with text flowing around it:

```
┌────────────────────────┐
│ Text flows    ┌──────┐ │
│ around the    │ IMG  │ │
│ image here    └──────┘ │
│ and then expands to    │
│ the full container     │
│ width below it.        │
└────────────────────────┘
```

::: code-group

```swift [iOS]
let containerWidth = 320.0
let imageWidth = 120.0
let imageHeight = 80.0

var cursor = LayoutCursor(segmentIndex: 0, graphemeIndex: 0)
var y = 0.0

while let line = Pretext.layoutNextLine(prepared, start: cursor, maxWidth: {
    y < imageHeight ? containerWidth - imageWidth - 8 : containerWidth
}()) {
    context.draw(Text(line.text), at: CGPoint(x: 0, y: y))
    cursor = line.end
    y += lineHeight
}
```

```kotlin [Android]
val containerWidth = 320f
val imageWidth = 120f
val imageHeight = 80f

var cursor = LayoutCursor(0, 0)
var y = 0f

while (true) {
    val width = if (y < imageHeight) containerWidth - imageWidth - 8f else containerWidth
    val line = Pretext.layoutNextLine(prepared, cursor, width) ?: break
    canvas.drawText(line.text, 0f, y + baseline, paint)
    cursor = line.end
    y += lineHeight
}
```

:::

## Multi-Column Layout

Flow text across multiple columns, like a newspaper:

```swift
var cursor = LayoutCursor(segmentIndex: 0, graphemeIndex: 0)
let columnWidth = (pageWidth - gutterWidth) / 2

for column in 0..<2 {
    let x = Double(column) * (columnWidth + gutterWidth)
    var y = 0.0

    while y < pageHeight {
        guard let line = Pretext.layoutNextLine(prepared, start: cursor, maxWidth: columnWidth) else { break }
        context.draw(Text(line.text), at: CGPoint(x: x, y: y))
        cursor = line.end
        y += lineHeight
    }
}
```

## The LayoutCursor

`LayoutCursor` tracks position within the prepared text:
- `segmentIndex` — which segment we're in
- `graphemeIndex` — which grapheme within that segment (0 at segment boundaries)

Pass `line.end` as the `start` of the next `layoutNextLine()` call to continue where the previous line left off. When `layoutNextLine` returns `nil`, all text has been consumed.
