---
layout: home
hero:
  name: Mobile-Pretext
  text: Pure-Arithmetic Text Layout
  tagline: Measure text height and compute line breaks without triggering native layout passes. For iOS and Android.
  image:
    src: /icon.png
    alt: Mobile-Pretext
  actions:
    - theme: brand
      text: Get Started
      link: /guide/getting-started
    - theme: alt
      text: View on GitHub
      link: https://github.com/MaTriXy/mobile-pretext
features:
  - title: Two-Phase Architecture
    details: prepare() once, layout() on every resize. Sub-microsecond per text block.
  - title: Every Language
    details: Hebrew, Arabic, Chinese, Japanese, Korean, Thai, Hindi, Myanmar, emoji, and mixed bidirectional text.
  - title: Rich Line API
    details: Get line contents, widths, and cursors. Flow text around obstacles with variable-width layout.
  - title: Native Platforms
    details: Swift Package for iOS (CoreText), Kotlin library for Android (TextPaint) with Jetpack Compose integration.
---
