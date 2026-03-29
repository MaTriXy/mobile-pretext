# Line Breaking

The line-breaking engine is the heart of Mobile-Pretext. It walks cached segment widths with pure arithmetic to determine where lines break.

## Rules

The engine matches CSS `white-space: normal` + `overflow-wrap: break-word`:

1. **Break before overflow** — when the next segment would exceed `maxWidth`, break before it
2. **Trailing whitespace hangs** — spaces at the end of a line don't count toward the line width
3. **Grapheme-level breaking** — words wider than `maxWidth` break at grapheme cluster boundaries (not byte or codepoint boundaries)

## Two Code Paths

### Simple fast path
Used when text has no soft hyphens, tabs, preserved spaces, or hard breaks — just regular text and spaces. This covers the vast majority of app text.

### Full path
Handles all 8 segment break kinds, including:
- **Chunks** for hard-break boundaries (pre-wrap mode)
- **Soft hyphen logic** — tries to fit more text before breaking at a soft hyphen; shows `-` when broken
- **Tab advances** — positions to next tab stop
- **Preserved spaces** — visible width that still allows breaking

## Soft Hyphen Behavior

Soft hyphens (`\u00AD`) are invisible discretionary break points:

```
"Supercalifragilisticexpialidocious"       → no hyphens shown
"Sup\u{AD}er\u{AD}cal\u{AD}i\u{AD}frag…"  → "Sup-" at break, invisible otherwise
```

When the engine chooses to break at a soft hyphen:
- The broken line gets a trailing `-` character
- The line width includes the hyphen width
- The next line starts after the soft hyphen

## Grapheme-Level Breaking

When a word is wider than `maxWidth` (common with CJK text or very narrow containers), the engine breaks at grapheme cluster boundaries:

```
"Pneumonoultramicroscopicsilicovolcanoconiosis" at 50px width:
  Line 1: "Pneumon"
  Line 2: "oultram"
  Line 3: "icrosco"
  ...
```

Grapheme clusters are the correct breaking unit because they represent what users perceive as a single character:
- `"é"` (e + combining accent) → one grapheme, won't break apart
- `"👨‍👩‍👧"` (family emoji) → one grapheme, won't break apart
- `"가"` (Korean syllable) → one grapheme

## Performance

The line-breaking engine operates on flat arrays with no allocations:

| Operation | Cost |
|-----------|------|
| `layout()` (count only) | ~0.0002ms per text |
| `layoutWithLines()` (with text) | ~0.01ms per text |
| `walkLineRanges()` (no text) | ~0.005ms per text |
| `layoutNextLine()` (per line) | ~0.001ms per line |
