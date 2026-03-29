# Pre-Wrap Mode

Pre-wrap mode preserves whitespace characters that are normally collapsed. Use it for editor-like text where spaces, tabs, and newlines should be visible and affect layout.

## Enabling Pre-Wrap

::: code-group

```swift [iOS]
let prepared = Pretext.prepare(editorText, font: font, options: PrepareOptions(whiteSpace: .preWrap))
let result = Pretext.layout(prepared, maxWidth: editorWidth, lineHeight: 20)
```

```kotlin [Android]
val prepared = Pretext.prepare(editorText, measurer, PrepareOptions(WhiteSpaceMode.PreWrap))
val result = Pretext.layout(prepared, maxWidth = editorWidth, lineHeight = 20f)
```

:::

## Behavior Differences

| Character | Normal mode | Pre-wrap mode |
|-----------|-------------|---------------|
| Space `" "` | Collapsed, zero-width at line end | Visible width, wrappable |
| Tab `\t` | Collapsed to space | Advances to next tab stop |
| Newline `\n` | Collapsed to space | Hard line break |
| Multiple spaces `"   "` | Collapsed to single space | Each space has visible width |

## Tab Stops

Tabs advance to the next tab stop position. Tab stops are spaced at 8 times the space width (matching the default browser `tab-size: 8`):

```
"Hello\tWorld"
 ↓
 Hello···World     (tab fills to next 8-space boundary)
```

The tab width depends on the current line position — a tab at position 0 advances 8 spaces, but a tab at position 3 advances only 5 spaces (to reach the next multiple of 8).

## Hard Breaks

In pre-wrap mode, `\n` characters force a new line regardless of available width. The text is split into "chunks" at hard break boundaries, and each chunk is laid out independently.

```
"Line one\nLine two\n\nLine four"
 ↓
 Line one
 Line two
 (empty line)
 Line four
```

Empty lines between consecutive `\n` characters are preserved and contribute to the total height.

## Use Cases

- **Text editors**: Measuring textarea content with preserved formatting
- **Code display**: Indentation and blank lines matter
- **Chat with formatting**: Messages that include intentional line breaks and spacing
- **Pre-formatted content**: Content pasted from external sources
