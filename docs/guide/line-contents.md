# Line Contents

When you need the actual text of each line (for custom rendering, text selection, or Canvas/Metal drawing), use the rich layout APIs.

## `layoutWithLines()`

Returns all lines at a fixed width with their text, width, and cursor boundaries.

::: code-group

```swift [iOS]
let prepared = Pretext.prepareWithSegments(text, font: font)
let result = Pretext.layoutWithLines(prepared, maxWidth: 300, lineHeight: 22)

for (i, line) in result.lines.enumerated() {
    context.draw(Text(line.text), at: CGPoint(x: 0, y: Double(i) * 22))
}
```

```kotlin [Android]
val prepared = Pretext.prepareWithSegments(text, measurer)
val result = Pretext.layoutWithLines(prepared, maxWidth = 300f, lineHeight = 22f)

for ((i, line) in result.lines.withIndex()) {
    canvas.drawText(line.text, 0f, i * 22f + baseline, paint)
}
```

:::

Each `LayoutLine` contains:
- `text` — the actual string content of the line
- `width` — measured width in pixels
- `start` — inclusive cursor (segment index + grapheme index)
- `end` — exclusive cursor

## `walkLineRanges()`

When you need line widths and boundaries but **not** the text strings (e.g., for shrinkwrap calculations), use this lighter API:

::: code-group

```swift [iOS]
var maxLineWidth = 0.0
Pretext.walkLineRanges(prepared, maxWidth: 300) { line in
    maxLineWidth = max(maxLineWidth, line.width)
}
// maxLineWidth is now the tightest container width
```

```kotlin [Android]
var maxLineWidth = 0f
Pretext.walkLineRanges(prepared, maxWidth = 300f) { line ->
    maxLineWidth = maxOf(maxLineWidth, line.width)
}
```

:::

## Shrinkwrap

Find the minimum width that fits text in a target number of lines:

::: code-group

```swift [iOS]
// Binary search for the tightest width
var lo = 50.0, hi = 500.0
while hi - lo > 1 {
    let mid = (lo + hi) / 2
    let count = Pretext.layout(PreparedText(core: prepared.core), maxWidth: mid, lineHeight: 20).lineCount
    if count <= targetLines { hi = mid } else { lo = mid }
}
```

```kotlin [Android]
var lo = 50f; var hi = 500f
while (hi - lo > 1f) {
    val mid = (lo + hi) / 2f
    val count = Pretext.layout(PreparedText(prepared.core), mid, 20f).lineCount
    if (count <= targetLines) hi = mid else lo = mid
}
```

:::
