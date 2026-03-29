# ``Pretext``

Pure-arithmetic text measurement and layout for iOS.

## Overview

Pretext computes paragraph height and line breaks using only cached font metrics and arithmetic — no `UITextView`, no `UILabel`, no layout reflow.

**Two-phase architecture:**
1. ``Pretext/prepare(_:font:options:)`` — one-time text analysis and measurement
2. ``Pretext/layout(_:maxWidth:lineHeight:)`` — pure arithmetic on cached widths

### Quick Start

```swift
import Pretext

let font = FontSpec(name: "Helvetica Neue", size: 16)
let prepared = Pretext.prepare("Hello 春天到了 🚀", font: font)
let result = Pretext.layout(prepared, maxWidth: 300, lineHeight: 22)
// result.lineCount = 1, result.height = 22.0
```

## Topics

### Essentials
- ``Pretext/prepare(_:font:options:)``
- ``Pretext/layout(_:maxWidth:lineHeight:)``
- ``FontSpec``
- ``PreparedText``
- ``LayoutResult``

### Rich Layout
- ``Pretext/prepareWithSegments(_:font:options:)``
- ``Pretext/layoutWithLines(_:maxWidth:lineHeight:)``
- ``Pretext/walkLineRanges(_:maxWidth:onLine:)``
- ``Pretext/layoutNextLine(_:start:maxWidth:)``
- ``PreparedTextWithSegments``
- ``LayoutLine``
- ``LayoutLineRange``
- ``LayoutCursor``
- ``LayoutLinesResult``

### Configuration
- ``PrepareOptions``
- ``WhiteSpaceMode``
- ``Pretext/clearCache()``
- ``Pretext/setLocale(_:)``
