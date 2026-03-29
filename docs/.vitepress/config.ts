import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Mobile-Pretext',
  description: 'Pure-arithmetic text measurement & layout for iOS and Android',
  base: '/mobile-pretext/',

  head: [
    ['meta', { name: 'theme-color', content: '#3366FF' }],
    ['link', { rel: 'icon', href: '/mobile-pretext/icon.png' }],
  ],

  themeConfig: {
    logo: '/icon.png',

    nav: [
      { text: 'Guide', link: '/guide/getting-started' },
      { text: 'API', items: [
        { text: 'iOS (DocC)', link: '/api/ios/' },
        { text: 'Android (Dokka)', link: '/api/android/' },
      ]},
      { text: 'GitHub', link: 'https://github.com/MaTriXy/mobile-pretext' },
    ],

    sidebar: {
      '/guide/': [
        {
          text: 'Introduction',
          items: [
            { text: 'What is Mobile-Pretext?', link: '/guide/what-is-it' },
            { text: 'Getting Started', link: '/guide/getting-started' },
          ],
        },
        {
          text: 'Core Concepts',
          items: [
            { text: 'Two-Phase Architecture', link: '/guide/architecture' },
            { text: 'Text Analysis', link: '/guide/text-analysis' },
            { text: 'Line Breaking', link: '/guide/line-breaking' },
            { text: 'Language Support', link: '/guide/languages' },
          ],
        },
        {
          text: 'API Guide',
          items: [
            { text: 'Height Measurement', link: '/guide/height-measurement' },
            { text: 'Line Contents', link: '/guide/line-contents' },
            { text: 'Variable-Width Layout', link: '/guide/variable-width' },
            { text: 'Pre-Wrap Mode', link: '/guide/pre-wrap' },
          ],
        },
        {
          text: 'Platform Reference',
          items: [
            { text: 'iOS (Swift)', link: '/guide/ios' },
            { text: 'Android (Kotlin)', link: '/guide/android' },
          ],
        },
      ],
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/MaTriXy/mobile-pretext' },
    ],

    search: {
      provider: 'local',
    },

    footer: {
      message: 'Copyright 2026 <a href="https://github.com/MaTriXy">MaTriXy</a>. All rights reserved. Released under the MIT License.',
    },
  },
})
