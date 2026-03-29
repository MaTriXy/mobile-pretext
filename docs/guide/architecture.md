# Two-Phase Architecture

Mobile-Pretext separates text processing into two distinct phases with very different performance characteristics.

## Phase 1: `prepare()`

**When**: Call once when text first appears (e.g., when a chat message loads).

**What it does**:
1. **Normalize whitespace** — collapse runs of spaces/tabs/newlines per CSS `white-space: normal` rules
2. **Segment into words** — using platform word segmenters (NLTokenizer on iOS, ICU BreakIterator on Android)
3. **Apply merge rules** — punctuation sticks to preceding words ("hello." is one unit), CJK kinsoku shori, URL detection, numeric runs
4. **Measure each segment** — using platform text measurement (CoreText on iOS, TextPaint on Android)
5. **Pre-measure graphemes** — for words that might need character-level breaking (overflow-wrap)
6. **Cache everything** — segment widths are cached by (font, segment) and shared across all prepared texts

**Cost**: ~19ms for 500 texts (batch). The measurement cache means repeated words across different texts are free.

**Output**: An opaque `PreparedText` handle containing parallel arrays of widths, break kinds, and grapheme data.

## Phase 2: `layout()`

**When**: Call on every resize, scroll position change, or width recalculation.

**What it does**:
1. Walk the cached segment widths
2. Apply line-breaking rules (CSS `white-space: normal` + `overflow-wrap: break-word`)
3. Count lines and compute height

**What it does NOT do**:
- No platform measurement calls
- No string operations
- No memory allocations
- No DOM/view hierarchy access

**Cost**: ~0.0002ms per text. That's 5,000 texts per millisecond.

## Why This Matters

Traditional text measurement interleaves reading and writing to the layout system:

```
measure(text1) → layout reflow → measure(text2) → layout reflow → ...
```

Each measurement forces the system to compute the full layout tree. With 500 items, this costs 30ms+ per frame.

Mobile-Pretext eliminates this entirely:

```
prepare(text1), prepare(text2), ...  → one-time, cached
layout(p1, width), layout(p2, width), ...  → pure arithmetic, instant
```

## The PreparedText Handle

`PreparedText` is intentionally opaque. It contains:

| Array | Purpose |
|-------|---------|
| `widths` | Measured width of each segment |
| `lineEndFitAdvances` | Width contribution for line-fitting decisions |
| `lineEndPaintAdvances` | Width contribution for visual display |
| `kinds` | Break behavior per segment (8 types) |
| `breakableWidths` | Per-grapheme widths for overflow wrapping |
| `chunks` | Hard-break boundaries (for pre-wrap mode) |

The handle is **width-independent** — the same prepared data works at any `maxWidth` and `lineHeight`. Prepare once, layout at every width.
