# GitHabit design contract

Scope: native baseline Material 3 (BOM 2025.09.01, Material3 1.4.0). No Expressive,
dynamic-color, dependency, package, data-format, or product-scope changes.

## Theme and source roles

- `ui/Theme.kt`: explicit light/dark action, container, error, inverse/snackbar,
  outline and surface roles. `onSurfaceVariant` is secondary copy, not disabled text.
- Background: scaffold; surface: cards/sheets/navigation; low through highest
  containers: increasing emphasis. `outline` is a meaningful boundary;
  `outlineVariant` is only decorative separation.
- `primary/onPrimary`: dominant action; `primaryContainer/onPrimaryContainer`:
  positive badge/brand container. Secondary-container pairing: tonal actions and
  selected chips/navigation. Error is for errors/destructive actions, not a habit category.
- `HabitAccentPalette`: six categorical accents, independently tuned per theme.
  Glyph backgrounds composite the accent at 11% over their enclosing surface.
  Accents also identify progress, with amounts in text. They are not success/error codes.
- `HeatmapPalette`: neutral zero plus four ordered intensity levels, not text or
  control colors. The existing chart still uses prototype daily-score semantics;
  Task 11 owns the activity legend, eligibility states, and accessible inspection redesign.
- `ui/DesignTokens.kt`: spacing 4/8/12/16/20/24dp; 48dp minimum ordinary control
  space; 24dp normal icons; 20dp supporting icons; 40dp glyphs; 1dp separators;
  2dp focus strokes; 4dp progress tracks. These are not fixed text heights.
- Material shape roles: 6/10/16/22/28dp corners. Compact containers use small,
  badges extraSmall, cards medium, larger groups/dialogs large/extraLarge.
- Material typography owns ordinary headings, body and labels. Body is 14/22sp,
  secondary copy and small labels 12/18sp. The original tightened bold display
  headings remain an intentional brand choice. `GitHabitType` owns wordmark,
  monospace eyebrow/chart label and numeric-statistic exceptions. All use `sp`.

## Effective theme and startup

`ThemePreference.isDark(systemDark)` resolves once in the app shell. Both Compose
and `GitHabitSystemBars` receive that Boolean. Explicit LIGHT/DARK ignore system
changes; SYSTEM observes configuration changes. Activity 1.11.0's `SystemBarStyle.auto`
uses the resolved choice for icons and API 26–28 scrims. API 29+ retains platform
three-button contrast protection. Insets remain consumed by the existing scaffold.

XML light/night resources follow the system at cold start. API 26 keeps a dark
navigation background; API 27+ declares theme-matched light/dark navigation icons.
The OS splash and initial loading frame cannot know a file-backed override before
the workspace loads. They follow SYSTEM, then switch to the verified preference.
A cold unreadable workspace also uses SYSTEM; recovery after a save failure retains
the last known workspace's theme. No duplicate preference store or blocking disk read.

## Interaction and motion policy

Keep baseline Material buttons, fields, chips, dialogs and sheets with their
semantics, enabled state, ripple and keyboard handling. Do not remove indication or
reduce the Material minimum interactive size. Prefer one clear dominant action,
not an absolute one-filled-button limit. Focus must not be signalled only by a faint
state-layer tint. `Modifier.focusOutline` adds a 2dp ring before the existing
Material focus target: primary on surface/tonal controls, onPrimary inside filled
primary actions. It observes the control's own focus (not a nested child's), adding
no second focus/click/semantics target and leaving the normal ripple intact. Applied
to shared cards/actions, chart controls and shell navigation; remaining destination-
specific controls are audited as their screens are updated and again in Task 13.

No app-owned essential animation, infinite decorative motion, custom duration-scale
override, or mandatory transition. Material motion uses the platform duration scale;
all state/content changes must remain understandable with animation disabled.
The existing chart's immediate scroll-to-end is not an animated transition.

## Verification boundary

`ThemeContractTest` measures actual sRGB token pairings (including alpha composition):
4.5:1 for copy/actions/errors against all defined surfaces, all on/container pairs,
and inverse/snackbar pairs; 3:1 for outline boundaries and habit glyph/progress inks.
The tests also verify all six preference/system combinations and ordered chart intensity.
`ThemeRenderingTest` verifies representative actual pixels for secondary copy,
positive badges, focused error fields and the keyboard-focused Log outline. It
checks all theme/system combinations, startup resource resolution, and a 320dp
shared card at 100%/200% text. See [Task 6 evidence](task6-verification.md).
This is not a complete rendered accessibility audit or TalkBack certification.

### Measured ratios (sRGB, rounded to two decimals)

| Foreground/background | Light | Dark | Minimum |
| --- | ---: | ---: | ---: |
| onSurface / worst defined surface | 11.87 | 10.41 | 4.5 |
| onSurfaceVariant / worst defined surface | 4.71 | 4.73 | 4.5 |
| primary / worst defined surface | 4.83 | 7.77 | 4.5 |
| error / worst defined surface | 4.85 | 6.16 | 4.5 |
| outline / worst defined surface | 3.33 | 3.57 | 3.0 |
| onPrimary / primary | 6.46 | 10.27 | 4.5 |
| onPrimaryContainer / primaryContainer | 7.31 | 9.43 | 4.5 |
| onSecondaryContainer / secondaryContainer | 8.56 | 8.47 | 4.5 |
| inverseOnSurface / inverseSurface | 12.39 | 13.27 | 4.5 |
| inversePrimary / inverseSurface | 9.24 | 5.40 | 4.5 |
| onErrorContainer / errorContainer | 8.69 | 8.69 | 4.5 |
| worst habit glyph / 11%-composited container | 3.67 | 4.55 | 3.0 |
| worst habit progress / surfaceContainerHighest | 4.72 | 6.66 | 3.0 |

The surface sweep includes background, surface, surfaceVariant, all five container
levels, surfaceDim and surfaceBright. Glyph sweep additionally includes the
selected-chip secondaryContainer. Tests also cover secondary/tertiary/error solid
pairs, tertiaryContainer, primary on primaryContainer and primary on secondaryContainer.
Disabled states and decorative separators are not asserted as enabled controls.
Antialiased glyph edges are not treated as the solid foreground reference.

### Consumer adoption and exceptions

Shared cards/panels/stats use medium shapes and role-based text; badges use
extraSmall/onPrimaryContainer. Compact stats stack label/value rather than squeezing
them into one line. Habit actions are a wrapping group below the activity streak.
The brand wordmark is one text node, and omits its redundant glyph at large text
in compact app bars. No arbitrary fixed height is applied to text blocks.

Heatmap geometry remains an intentional data-display exception, not a control size:
pitch is max(14dp, scaled chart-label line height), gap 3dp, corner 2dp, legend cells
7dp. Labels and chart use the same pitch; pointer mapping is refreshed when density
changes. A 1dp inset prevents today's outline being clipped at the chart edge.
Period/legend semantics and tiny pointer actions remain Task 11 work; this task does
not claim the existing chart is fully accessible. Brand Canvas proportions/strokes
and the half-size glyph icon ratio are also intentional artwork geometry.

References checked for this task:
- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- https://developer.android.com/reference/kotlin/androidx/activity/SystemBarStyle
- https://developer.android.com/develop/ui/compose/system/system-bars

The live SystemBarStyle docs now mention deprecation in Activity 1.14 alpha; that
does not apply to this pinned 1.11.0 build. Do not migrate to an alpha for this task.
