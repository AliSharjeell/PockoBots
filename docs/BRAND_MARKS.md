# Brand marks

The vendor marks in `android/app/src/main/res/drawable/brand_*.xml` are Android vector
drawables. They are monochrome on purpose: the app tints each one at runtime, so a mark keeps
its shape in the light theme and in the dark theme instead of carrying one baked-in colour.

## Sources

| Drawable | Mark | Source |
|---|---|---|
| `brand_codex.xml` | The OpenAI blossom Codex uses | Path data from `packages/brand/src/provider-logo-shape.ts` in OpenBot (`C:\AppsNew\openbot`), on a 256x260 grid |
| `brand_claude.xml` | The Claude asterisk | `claude.ai/favicon.svg`, on a 248x248 grid, via the same OpenBot brand package |
| `brand_gemini.xml` | The Gemini spark | [Simple Icons](https://simpleicons.org/?q=googlegemini) (CC0), on a 24x24 grid |
| `brand_grok.xml` | The Grok mark | Path data from the OpenBot brand package, on a 24x24 grid |
| `brand_opencode.xml` | The OpenCode frame | `packages/ui/src/assets/favicon/favicon.svg` in `anomalyco/opencode`, on a 512x512 grid |
| `brand_cursor.xml` | The Cursor cube | [Simple Icons](https://simpleicons.org/?q=cursor) (CC0), on a 24x24 grid |

OpenBot keeps the same marks as path data rather than as image files, so that its desktop app,
its mobile app and its API all draw one identical mark. PockoBots follows that approach.

## Rendering

Draw them with a tint so the mark takes the provider's colour:

```kotlin
Icon(painterResource(R.drawable.brand_claude), null, tint = brandColor)
```

OpenBot's rule is that a provider with no brand colour of its own inherits the primary text
colour, so a mark never borrows another provider's hue. The same rule applies here: only Claude
and Gemini carry a colour of their own.
