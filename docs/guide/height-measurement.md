# Height Measurement

The most common use case: compute how tall a paragraph will be at a given width, without triggering any native layout.

## Basic Usage

::: code-group

```swift [iOS]
let font = FontSpec(name: "Helvetica Neue", size: 16)
let prepared = Pretext.prepare(paragraphText, font: font)

// On every width change (resize, rotation, animation):
let result = Pretext.layout(prepared, maxWidth: containerWidth, lineHeight: 22)
let height = result.height      // e.g., 88.0
let lineCount = result.lineCount // e.g., 4
```

```kotlin [Android]
val measurer = PaintTextMeasurer(textPaint)
val prepared = Pretext.prepare(paragraphText, measurer)

// On every width change:
val result = Pretext.layout(prepared, maxWidth = containerWidth, lineHeight = 22f)
val height = result.height      // e.g., 88.0
val lineCount = result.lineCount // e.g., 4
```

:::

## Use Cases

### Virtualized lists
Pre-compute row heights for `UICollectionView` / `LazyColumn` without rendering any cells:

::: code-group

```swift [iOS]
// In your data source, prepare all texts upfront
let preparedMessages = messages.map {
    Pretext.prepare($0.text, font: messageFont)
}

// In sizeForItemAt — instant, no layout
func collectionView(_ cv: UICollectionView, ..., sizeForItemAt indexPath: IndexPath) -> CGSize {
    let result = Pretext.layout(preparedMessages[indexPath.item],
                                maxWidth: cv.bounds.width - 32,
                                lineHeight: 20)
    return CGSize(width: cv.bounds.width, height: result.height + padding)
}
```

```kotlin [Android]
// Prepare all texts upfront
val preparedMessages = messages.map {
    Pretext.prepare(it.text, measurer)
}

// In LazyColumn, use precomputed heights for smooth scrolling
LazyColumn {
    itemsIndexed(preparedMessages) { index, prepared ->
        val height = Pretext.layout(prepared, maxWidth, lineHeight).height
        MessageBubble(
            modifier = Modifier.height(with(density) { height.toDp() }),
            message = messages[index]
        )
    }
}
```

:::

### Scroll anchoring
When new content loads above the viewport, compute its height to adjust scroll position:

```
newContentHeight = sum of layout(prepared, width, lineHeight).height for new items
scrollView.contentOffset.y += newContentHeight
```

### Layout shift prevention
Compute height before rendering to reserve the exact space needed, preventing content from jumping.

## Performance

| Operation | Time | Notes |
|-----------|------|-------|
| `prepare()` per text | ~0.04ms | One-time, cached by font |
| `layout()` per text | ~0.0002ms | Pure arithmetic |
| 500 texts prepare | ~19ms | Batch, warm cache |
| 500 texts layout | ~0.09ms | All widths |
