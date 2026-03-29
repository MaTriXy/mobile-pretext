# Getting Started

## Installation

::: code-group

```swift [iOS — Swift Package Manager]
// In your Package.swift dependencies:
.package(url: "https://github.com/MaTriXy/mobile-pretext.git", from: "1.0.0")

// In your target dependencies:
.product(name: "Pretext", package: "mobile-pretext")
```

```kotlin [Android — Gradle]
// In your settings.gradle.kts, include the modules:
include(":pretext-core")
include(":pretext-android")
include(":pretext-compose")  // optional, for Compose integration

// In your app's build.gradle.kts:
dependencies {
    implementation(project(":pretext-core"))
    implementation(project(":pretext-android"))
    implementation(project(":pretext-compose"))  // optional
}
```

:::

## Quick Start

### 1. Measure Text Height

The most common use case — compute how tall a paragraph will be at a given width.

::: code-group

```swift [iOS]
import Pretext

let font = FontSpec(name: "Helvetica Neue", size: 16)
let prepared = Pretext.prepare("Hello 春天到了 🚀", font: font)

// Call on every resize — sub-microsecond
let result = Pretext.layout(prepared, maxWidth: 300, lineHeight: 22)
print("Lines: \(result.lineCount), Height: \(result.height)")
```

```kotlin [Android]
import com.pretext.Pretext
import com.pretext.android.PaintTextMeasurer
import com.pretext.android.IcuTextSegmenter

// One-time setup
Pretext.setSegmenter(IcuTextSegmenter())

val measurer = PaintTextMeasurer(myTextPaint)
val prepared = Pretext.prepare("Hello 春天到了 🚀", measurer)

// Call on every resize — sub-microsecond
val result = Pretext.layout(prepared, maxWidth = 300f, lineHeight = 22f)
println("Lines: ${result.lineCount}, Height: ${result.height}")
```

:::

### 2. Get Line Contents

Switch from `prepare` to `prepareWithSegments` for per-line data.

::: code-group

```swift [iOS]
let prepared = Pretext.prepareWithSegments(text, font: font)
let result = Pretext.layoutWithLines(prepared, maxWidth: 300, lineHeight: 22)

for line in result.lines {
    print("\(line.text) — width: \(line.width)")
}
```

```kotlin [Android]
val prepared = Pretext.prepareWithSegments(text, measurer)
val result = Pretext.layoutWithLines(prepared, maxWidth = 300f, lineHeight = 22f)

for (line in result.lines) {
    println("${line.text} — width: ${line.width}")
}
```

:::

### 3. Variable-Width Layout

Flow text around obstacles (like floated images) using `layoutNextLine`.

::: code-group

```swift [iOS]
let prepared = Pretext.prepareWithSegments(text, font: font)
var cursor = LayoutCursor(segmentIndex: 0, graphemeIndex: 0)
var y = 0.0

while let line = Pretext.layoutNextLine(prepared, start: cursor, maxWidth: widthAt(y)) {
    renderLine(line, at: y)
    cursor = line.end
    y += lineHeight
}
```

```kotlin [Android]
val prepared = Pretext.prepareWithSegments(text, measurer)
var cursor = LayoutCursor(0, 0)
var y = 0f

while (true) {
    val line = Pretext.layoutNextLine(prepared, cursor, widthAt(y)) ?: break
    renderLine(line, y)
    cursor = line.end
    y += lineHeight
}
```

:::

## Next Steps

- [Two-Phase Architecture](/guide/architecture) — understand why it's fast
- [Language Support](/guide/languages) — CJK, Arabic, emoji, and more
- [iOS Platform Guide](/guide/ios) — CoreText, SwiftUI integration
- [Android Platform Guide](/guide/android) — TextPaint, Compose integration
