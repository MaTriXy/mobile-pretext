# Text Analysis

The analysis phase transforms raw text into a structured sequence of segments ready for measurement and line breaking.

## Pipeline

```
Raw text
  → Whitespace normalization
  → Word segmentation (NLTokenizer / ICU BreakIterator)
  → Break-kind classification (8 types)
  → Merge passes (punctuation, URLs, CJK, Arabic, etc.)
  → Structured segments ready for measurement
```

## Whitespace Normalization

### Normal mode (default)
Matches CSS `white-space: normal`:
- Collapse `\t`, `\n`, `\r`, `\f`, and multiple spaces into a single space
- Strip leading and trailing spaces

### Pre-wrap mode
Matches CSS `white-space: pre-wrap`:
- Preserve ordinary spaces (visible, but wrappable)
- Preserve tabs (advance to next tab stop)
- Preserve hard breaks (`\n`)
- Normalize `\r\n` → `\n` and `\r` → `\n`

## Segment Break Kinds

Each segment is classified into one of 8 break kinds:

| Kind | Examples | Behavior |
|------|----------|----------|
| `text` | `"hello"`, `"世界"` | No break within; break before if overflow |
| `space` | `" "` | Break point; trailing space hangs past line edge |
| `preservedSpace` | `" "` (pre-wrap) | Visible width; break point |
| `tab` | `"\t"` (pre-wrap) | Advance to next tab stop (8 × space width) |
| `glue` | NBSP `\u00A0`, NNBSP `\u202F` | No break; width like a space |
| `zeroWidthBreak` | ZWSP `\u200B` | Break point with zero width |
| `softHyphen` | `\u00AD` | Break point; shows `-` only when broken |
| `hardBreak` | `\n` (pre-wrap) | Forced line break |

## Merge Rules

After initial segmentation, several merge passes combine segments:

### Punctuation merging
`"hello" + "."` → `"hello."` — punctuation sticks to the preceding word.

### CJK kinsoku shori
Japanese/Chinese line-breaking rules:
- **Line-start prohibited**: `。、！：；？）」` etc. — these characters can't start a line
- **Line-end prohibited**: `「（【` etc. — these characters can't end a line

### URL detection
`"https" + ":" + "//" + "example.com"` → `"https://example.com"` — URLs stay as one unit.

### Numeric runs
`"123" + "-" + "456"` → `"123-456"` — phone numbers, dates, and numeric patterns stay together.

### NBSP glue
`"100" + "\u00A0" + "km"` → `"100\u00A0km"` — non-breaking spaces bind adjacent text.

### Arabic no-space punctuation
Arabic punctuation like `:` and `,` can directly precede the next word without a space.

### Myanmar medial glue
Myanmar's `\u104F` character binds to the following segment.
