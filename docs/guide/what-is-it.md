# What is Mobile-Pretext?

Mobile-Pretext is a native port of [pretext](https://github.com/chenglou/pretext) — a pure-arithmetic text measurement and layout library. It computes paragraph height and line breaks **without triggering native layout passes**.

## The Problem

On both iOS and Android, measuring text is expensive:

- **iOS**: `UITextView.sizeThatFits()`, `NSAttributedString.boundingRect()` — each triggers a full text layout pass
- **Android**: `StaticLayout`, `BoringLayout` — allocates objects and runs the full line-breaking algorithm

When you have hundreds of text blocks (chat messages, feed items, list cells), measuring them all forces the system to do redundant work on every resize, scroll, or rotation.

## The Solution

Mobile-Pretext splits text measurement into two phases:

1. **`prepare(text, font)`** — Analyze the text once: normalize whitespace, segment into words, measure each segment's width, and cache everything. This is the expensive step (~19ms for 500 texts).

2. **`layout(prepared, maxWidth, lineHeight)`** — Walk the cached widths with pure arithmetic to count lines and compute height. No platform calls, no allocations. This is the cheap step (~0.0002ms per text).

The key insight: `prepare()` is **width-independent**. The same prepared handle can be laid out at any `maxWidth` and `lineHeight`. So you prepare once when text appears, and layout instantly on every resize.

## What It Handles

- **All scripts**: Hebrew, Arabic, Chinese, Japanese, Korean, Thai, Hindi, Myanmar, and more
- **Emoji**: Correct width measurement for all emoji including ZWJ sequences
- **CJK line breaking**: Kinsoku shori (line-start/end prohibited characters)
- **Soft hyphens**: Discretionary break points with visible hyphens when broken
- **Non-breaking spaces**: NBSP, narrow NBSP, word joiner
- **Pre-wrap mode**: Preserved spaces, tabs, and hard breaks
- **Bidirectional text**: Embedding levels for custom RTL rendering
- **Overflow wrapping**: Break inside long words at grapheme cluster boundaries

## Architecture

```
Text → [Analysis] → [Measurement] → PreparedText → [Line Breaking] → LayoutResult
         │               │                              │
    Segmentation    CoreText/TextPaint          Pure arithmetic
    Merge rules     Width caching               No platform calls
    CJK kinsoku     Grapheme widths             ~0.0002ms/text
```

Both platforms share the same algorithm ported from the TypeScript original. The only platform-specific code is:
- **Measurement**: CoreText (iOS) / TextPaint (Android)
- **Word segmentation**: NLTokenizer (iOS) / ICU BreakIterator (Android)
