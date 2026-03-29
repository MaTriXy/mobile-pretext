# Android Platform Guide

## Installation

The Android library is organized as three Gradle modules:

| Module | Purpose | Android dependency? |
|--------|---------|---------------------|
| `pretext-core` | Types, algorithms, public API | No (pure Kotlin/JVM) |
| `pretext-android` | TextPaint measurer, ICU segmenter | Yes (API 24+) |
| `pretext-compose` | `PretextText` composable | Yes (Compose) |

```kotlin
// settings.gradle.kts
include(":pretext-core")
include(":pretext-android")
include(":pretext-compose")  // optional

// app/build.gradle.kts
dependencies {
    implementation(project(":pretext-core"))
    implementation(project(":pretext-android"))
    implementation(project(":pretext-compose"))  // optional
}
```

## Platform-Specific Details

### Setup

Android requires a one-time segmenter registration before any `prepare()` call:

```kotlin
// In Application.onCreate() or Activity.onCreate()
Pretext.setSegmenter(IcuTextSegmenter())
```

### Font Specification

Android uses `TextPaint` wrapped in `PaintTextMeasurer`:

```kotlin
val paint = TextPaint().apply {
    textSize = 16f * resources.displayMetrics.scaledDensity  // sp to px
    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
}
val measurer = PaintTextMeasurer(paint)
```

Make sure `textSize` is in **pixels**, not sp. Convert using density:

```kotlin
val textSizePx = with(density) { 16.sp.toPx() }
```

### Measurement Engine

Android uses **TextPaint.measureText()** for segment width measurement. This uses the Skia/HarfBuzz text shaping pipeline — the same engine Android uses for actual rendering.

Unlike the browser version, **no emoji width correction is needed** — `TextPaint` measures emoji consistently.

### Word Segmentation

Android uses **ICU BreakIterator** (`android.icu.text.BreakIterator`) for word boundary detection. Requires **API 24+** (covers 97%+ of active devices).

ICU provides:
- Word boundaries via `getWordInstance()` with rule status classification
- Grapheme cluster boundaries via `getCharacterInstance()`
- Full Unicode support for all scripts

### Thread Safety

The measurement cache uses `synchronized` blocks and the segmenter field is `@Volatile`. You can call `prepare()` and `layout()` from any thread.

## Jetpack Compose Integration

### Drop-in composable

```kotlin
@Composable
fun MessageBubble(text: String) {
    PretextText(
        text = text,
        style = TextStyle(fontSize = 16.sp),
        lineHeight = 22.sp,
        modifier = Modifier.fillMaxWidth()
    )
}
```

`PretextText` handles `prepare()` caching internally via `remember`. It creates its own `PaintTextMeasurer` and `IcuTextSegmenter`.

### Manual usage in Compose

For more control, use the API directly:

```kotlin
@Composable
fun CustomTextLayout(text: String) {
    val density = LocalDensity.current
    val paint = remember { TextPaint().apply { textSize = with(density) { 16.sp.toPx() } } }
    val measurer = remember(paint) { PaintTextMeasurer(paint) }
    val prepared = remember(text) { Pretext.prepare(text, measurer) }
    val lineHeightPx = with(density) { 22.sp.toPx() }

    Layout(content = {}) { _, constraints ->
        val result = Pretext.layout(prepared, constraints.maxWidth.toFloat(), lineHeightPx)
        layout(constraints.maxWidth, result.height.roundToInt()) {}
    }
}
```

### LazyColumn with precomputed heights

```kotlin
@Composable
fun MessageList(messages: List<Message>) {
    val measurer = remember { PaintTextMeasurer(TextPaint().apply { textSize = 48f }) }
    val prepared = remember(messages) { messages.map { Pretext.prepare(it.text, measurer) } }

    LazyColumn {
        itemsIndexed(prepared) { i, p ->
            val height = Pretext.layout(p, maxWidth, lineHeight).height
            MessageRow(messages[i], Modifier.height(with(density) { height.toDp() }))
        }
    }
}
```

## Dokka API Reference

The Kotlin library includes KDoc comments on all public APIs. Generate HTML docs with:

```sh
cd android
./gradlew :pretext-core:dokkaHtml
```

Output goes to `pretext-core/build/dokka/html/`.

## Sample App

```sh
cd android
studio .  # or open in Android Studio
```

Select the `sample-app` run configuration and run on your device.
