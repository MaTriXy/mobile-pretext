# Language Support

Mobile-Pretext handles text in any script that the platform's word segmenter and font engine support. No special configuration is needed — all languages work out of the box.

## Tested Scripts

| Script | Languages | Special handling |
|--------|-----------|-----------------|
| Latin | English, French, German, etc. | Standard word-boundary breaking |
| Hebrew | Hebrew | RTL bidi levels |
| Arabic | Arabic, Urdu, Farsi | RTL bidi levels, no-space punctuation rules |
| CJK | Chinese, Japanese, Korean | Per-character breaking, kinsoku shori |
| Thai | Thai | Dictionary-based word segmentation |
| Devanagari | Hindi, Sanskrit | Combining mark handling |
| Myanmar | Burmese | Medial glue rules |
| Khmer | Cambodian | Dictionary-based segmentation |
| Lao | Lao | Dictionary-based segmentation |
| Korean | Korean | Hangul syllable-level breaking |
| Emoji | All platforms | Correct ZWJ sequence widths |

## CJK Line Breaking

Chinese, Japanese, and Korean text uses per-character line breaking — each grapheme is a potential break point. This matches browser behavior.

### Kinsoku Shori

Japanese/Chinese line-breaking rules prevent certain characters from appearing at line boundaries:

**Line-start prohibited** (can't start a line):
`。、！：；？）」】＞」」` and iteration marks `ゝゞヽヾ々〻`

**Line-end prohibited** (can't end a line):
`"（「【＜「` and opening quotes `"'«‹`

## Bidirectional Text

Mixed LTR/RTL text (e.g., English mixed with Hebrew or Arabic) gets bidi embedding levels computed via a simplified Unicode Bidi Algorithm. These levels are available on the `PreparedTextWithSegments` handle for custom rendering.

::: info
The line-breaking engine itself does not reorder text based on bidi levels — it breaks lines in logical order. Bidi levels are metadata for renderers that need to display text in visual order.
:::

## Emoji

All emoji are supported, including:
- Basic emoji (`😀`, `🚀`)
- Emoji with variation selectors (`❤️`)
- Skin tone modifiers (`👋🏽`)
- ZWJ sequences (`👨‍👩‍👧`, `🏳️‍🌈`)
- Regional indicators / flag sequences (`🇯🇵`)

Each emoji grapheme cluster is measured as a single unit with correct width.
