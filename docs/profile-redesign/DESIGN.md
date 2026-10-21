# Social Profile Redesign — Twitter-style layout, Animite identity

This document is the design-token / style-guide deliverable for the profile
redesign. The app is a native Android app (Kotlin + Jetpack Compose), so the
parts of a generic web brief that mention Figma files, CSS/SCSS and React/Vue
components are delivered in the form this codebase actually uses:

| Brief item (web)        | Delivered here (Android)                                        |
| ----------------------- | --------------------------------------------------------------- |
| Design tokens           | `AnimiteTheme` colour scheme + typography (this document)       |
| CSS variables / SCSS    | `MaterialTheme.colorScheme` / `typography` tokens, no hardcoding |
| Reusable components     | Compose components in `UserProfileScreen.kt` (see inventory)    |
| Figma/Sketch file       | This spec + the running screen; sizes below are exact dp values |
| API endpoints           | Firestore queries in the Data/API section                       |

File changed: `social/.../ui/profile/UserProfileScreen.kt` (+ ViewModel,
`CommentRepository.getCommentsByUser`, one Firestore composite index).

## Layout

Single centred column, `widthIn(max = 600.dp)` on large screens
(tablets/foldables), mirroring Twitter's profile column:

1. **App bar** — display name (bold) + `{count} posts` subtitle, back /
   share / overflow (block, report) actions.
2. **Cover** — full-bleed banner image (`bannerUrl`) or a brand gradient
   (`primary → tertiary`). Own profile: tap the cover to change it
   (camera badge, bottom-end).
3. **Avatar row** — circular avatar overlapping the cover's bottom edge by
   ~half, ring = `background` colour, 4.dp. Own profile: camera badge +
   tap-to-change with upload spinner overlay. Right side of the row:
   **Edit profile** pill (own) or message icon-button + **Follow** pill.
4. **Identity** — name (`titleLarge`, bold, heading semantics), `@handle`,
   bio (max 4 lines in the header), meta row (📅 Joined *Month Year*),
   private lock when applicable.
5. **Stats** — Twitter order: **Following** first, then **Followers**;
   counts formatted compactly (`950`, `1.2K`, `3.4M`); both tappable and
   navigate to the existing follow-list screens.
6. **Tabs** — Posts / Replies / Media in a `HorizontalPager` with a
   `PrimaryTabRow`; the indicator animates and swiping switches tabs.
   - *Posts*: the user's top-level comments, Twitter-style rows
     (avatar, name, `@handle · 2h`, content, target chip
     `Episode 5 · Anime #123`, reply/like counts).
   - *Replies*: the user's replies only.
   - *Media*: 3-column grid aggregating the anime/episodes the user
     posted about (gradient tile, post-count badge, episode label).

## Colour tokens (brand identity)

No Twitter blue anywhere. Everything resolves through
`MaterialTheme.colorScheme`, whose values come from `AnimiteTheme`
(MaterialKolor dynamic scheme seeded by the app's time-of-day palette,
or the system dynamic scheme when enabled; Cairo typography for Arabic).

| Token                | Used for                                             |
| -------------------- | ---------------------------------------------------- |
| `primary`            | Follow button, tab indicator, count badges           |
| `onPrimary`          | Text/icons on `primary`                              |
| `primary → tertiary` | Cover gradient fallback                              |
| `background`         | Avatar ring against the cover                        |
| `surface`            | App bar, tab row                                     |
| `onSurface`          | Name, bio, post text                                 |
| `onSurfaceVariant`   | @handle, meta, timestamps, secondary icons           |
| `surfaceVariant`     | Target chips, message button, private notice         |
| `outline`            | Message button border                                |
| `outlineVariant`     | Dividers                                             |
| `error`              | Unfollow / Block destructive actions                 |

Contrast: primary text uses `onSurface`, secondary text `onSurfaceVariant`
(both meet Material 3 contrast guidance on `surface`/`background` in light,
dark and AMOLED variants). The only hard-coded colours left on the screen
are inside the *Linked AniList* card (`#0B1622`, `#02A9FF`) — that is
AniList's own brand, deliberately scoped to that card only.

## Typography

`AnimiteTypography`, swapped for `CairoTypography` under an Arabic locale
(see `app/theme/Type.kt`). Roles: name `titleLarge` bold, app-bar name
`titleMedium` bold, handle/bio/post `bodyMedium`, meta/timestamps
`bodySmall`, chips `labelSmall`, stats counts `bodyMedium` bold.

## Spacing & breakpoints

- Page horizontal padding: 16.dp; vertical rhythm: 8/10/12.dp.
- Breakpoint `< 360.dp` (compact): cover 120.dp, avatar 68.dp.
- Default: cover 150.dp, avatar 84.dp.
- `≥ 600.dp`: content capped at 600.dp and centred.
- Touch targets: icon buttons 40–48.dp; follow pill height ≥ 40.dp.

## Interaction states

| Element        | States                                                                 |
| -------------- | ---------------------------------------------------------------------- |
| Follow         | Filled `primary` pill ("Follow") → follows immediately                 |
| Following      | Outlined pill; **pressed** state previews "Unfollow" in `error`; tap opens a confirmation dialog ("Unfollow @handle?") — unfollow only happens on confirm |
| Tabs           | Tap or swipe; indicator animates (`PrimaryTabRow` + pager)             |
| Avatar / cover | Own profile only; upload shows scrim + spinner; errors via snackbar with Retry |
| Stats          | Ripple via theme ripple configuration (`primary`-tinted)               |
| Content        | Loading spinner → content; per-tab empty states with guidance text     |

## Data / API (Firestore)

| Need               | Source                                                                 |
| ------------------ | ---------------------------------------------------------------------- |
| Profile            | `users/{uid}` snapshot listener (`UserProfileRepository.getUserProfile`) |
| Posts / Replies    | **New:** `comments where authorId == uid order by createdAt desc, limit 50` (`CommentRepository.getCommentsByUser`); Posts filters `parentId == null`, Replies the rest |
| Follow state       | `SocialRelationshipRepository.isFollowing` + follow/unfollow writes     |
| Followers/Following| Counts on the user document; lists via existing follow-list screens    |
| Avatar / cover     | Cloudinary upload, URL written back to `users/{uid}` (`photoUrl` / `bannerUrl`) |

**Deploy requirement:** the posts query needs the composite index
`comments (authorId ASC, createdAt DESC)`, added to `firestore.indexes.json`:

```sh
firebase deploy --only firestore:indexes
```

## Caching & performance

- Firestore snapshot flows are `stateIn(WhileSubscribed(1000))` — listeners
  detach when the screen leaves, reattach on return.
- Posts list and media grid are lazy (`LazyColumn` / `LazyVerticalGrid`);
  images go through Coil with the app's existing memory/disk cache setup.
- No per-item network calls in list rows (media tiles render from data
  already inside the comment documents).

## QA checklist

Manual (no Android SDK in the assistant environment — run on device):

- [ ] `./gradlew :social:testDebugUnitTest :social:compileDebugKotlin`
- [ ] Compact phone (<360.dp), standard phone, tablet/foldable (600.dp cap)
- [ ] Light / dark / AMOLED; system dynamic colour on/off
- [ ] Arabic locale (RTL + Cairo) and English
- [ ] Own profile: edit dialog, avatar change (incl. error + Retry),
      cover change; other profile: follow → Following, press-and-hold
      previews Unfollow, tap asks for confirmation, message button, menu
      (block/report dialogs intact)
- [ ] Tabs: swipe + tap, indicator animates, empty states on a fresh account
- [ ] Accessibility: TalkBack order (name is a heading), all icon buttons
      have content descriptions, text scales at 200% font size without
      clipping the stats row, contrast of `onSurfaceVariant` text
- [ ] Performance: scroll the Posts tab with 50 posts — no jank; leave and
      return — listener reattaches and data is current

## Known limitation / next step

Comment documents do not store anime cover URLs, so Media tiles are brand
gradient tiles labelled with the anime id / episodes. Storing `coverUrl`
(and the anime title) on comment creation would upgrade the Media tab to
real cover art with no layout change.
