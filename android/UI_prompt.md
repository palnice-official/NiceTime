# NiceTime Android App — Netflix-Inspired UI Recreation Prompt

## Role

You are an experienced Android UI engineer working inside the existing NiceTime Android project.

Your task is to **recreate and redesign the existing app UI** into a polished, premium streaming-app experience inspired by modern streaming platforms such as Netflix, while **preserving NiceTime's own identity and all existing application functionality**.

The attached/reference mockup is the primary visual direction. The current app screenshots show the existing implementation and should be used to understand the current information architecture and functionality.

IMPORTANT:
- Do NOT create a pixel-for-pixel Netflix clone.
- Do NOT copy Netflix branding, logos, assets, or proprietary UI.
- Use Netflix only as UX/design inspiration.
- Keep the product name **NiceTime**.
- Keep the app's existing backend/data/source/playback functionality unless a change is explicitly required by this task.
- Before modifying code, inspect the existing project structure and determine whether the project uses Jetpack Compose or XML Views. Follow the existing architecture instead of unnecessarily migrating frameworks.
- Reuse existing models, repositories, networking, navigation, storage, playback, and business logic whenever possible.
- Do not replace working functionality merely to achieve the visual redesign.

---

# 1. PRIMARY DESIGN GOAL

Transform the current UI from a basic dark application into a **cinematic, modern streaming application**.

Target feeling:

- Premium
- Cinematic
- Minimal
- Dark
- Modern
- Easy to browse
- Content-first
- Smooth
- Familiar to streaming-app users

The visual language should be:

- Near-black / charcoal backgrounds
- Dark elevated surfaces
- Lavender/purple as the primary accent
- White primary text
- Muted gray secondary text
- Rounded cards
- Large cinematic imagery
- Horizontal content rails
- Subtle animations
- Strong visual hierarchy
- Comfortable spacing

Do not overuse cards, borders, gradients, or shadows.

---

# 2. DESIGN SYSTEM

## Colors

Use a centralized theme rather than hardcoding colors throughout the UI.

Suggested direction:

- Background: near-black / very dark charcoal
- Surface: dark charcoal slightly lighter than background
- Elevated surface: medium-dark gray
- Primary accent: lavender / light purple
- Accent pressed state: darker purple
- Primary text: near-white
- Secondary text: muted gray
- Disabled text: darker gray
- Progress indicator: lavender
- Success/downloaded indicator: subtle green
- Error: subtle red

The exact values can be chosen based on the existing project/theme, but maintain visual consistency across every screen.

## Typography

Use a clean modern Android-compatible font system.

Hierarchy:

- Large hero title
- Large screen title
- Section title
- Movie title
- Metadata
- Description
- Secondary/helper text

Avoid excessive font sizes.

Use appropriate font weights rather than making everything bold.

---

# 3. GLOBAL LAYOUT

The app should use edge-to-edge layouts where appropriate and respect:

- Status bar
- Navigation bar
- Display cutouts
- Gesture navigation
- Different screen sizes

Avoid content being hidden behind system bars.

Use consistent horizontal page padding.

Use reusable spacing constants.

---

# 4. BOTTOM NAVIGATION

Replace the current visual style of the bottom navigation with a premium streaming-style navigation bar.

Primary navigation:

1. Home
2. Search
3. Downloads
4. You

Use:

- Icon
- Small label
- Selected-state pill/highlight
- Lavender selected icon
- Subtle animation when changing tabs

The selected tab should be obvious without being visually heavy.

Navigation should remain stable while switching between primary destinations.

---

# 5. HOME SCREEN

The Home screen should become the primary content-discovery screen.

Recommended structure:

```text
Top bar
    NiceTime logo/name
    Search icon
    Profile icon

Large Hero
    Cinematic background/poster
    Gradient overlay
    Content badge
    Title
    Year / type / metadata
    Play button
    My List button

Trending Now
    Horizontal poster rail

Continue Watching
    Horizontal landscape-card rail
    Progress indicators
    Remaining time

Popular Movies
    Horizontal poster rail

Recently Added / Recommended
    Horizontal poster rail
```

## Hero section

The hero should dominate the upper portion of the screen.

Include:

- Background image
- Dark gradient overlay
- Movie title
- Metadata
- Primary Play button
- Secondary My List button

Do not allow text to become unreadable over the image.

If an image is unavailable, gracefully fall back to the existing placeholder behavior.

Hero content should be driven by real application data where available. Do not permanently hardcode "Big Buck Bunny" as the production hero.

## Content rails

Create reusable horizontal content-rail components.

Example:

```text
Trending Now        See all >
[poster][poster][poster][poster]
```

Requirements:

- Horizontal scrolling
- Smooth scrolling
- Consistent card dimensions
- Poster aspect ratio preserved
- Movie title below where appropriate
- Tap opens movie details
- Avoid unnecessary nested vertical scrolling problems

---

# 6. CONTINUE WATCHING

Continue Watching should use landscape cards.

Each card should contain:

- Thumbnail/poster
- Movie/series title
- Episode/metadata when available
- Progress bar
- Remaining time
- Optional play overlay

Example:

```text
┌──────────────────────┐
│                      │
│      thumbnail       │
│                      │
└──────────────────────┘
Movie title
S1:E3 • 32m left
━━━━━━━━━━━━━━
```

Use actual watch-progress data from the existing application.

Do not invent progress values.

If Watch History is disabled, respect the existing preference behavior.

---

# 7. SEARCH SCREEN

Rename/reframe Explore as the primary **Search** experience while preserving existing Explore functionality where required.

Structure:

```text
Search

[ Search movies and series... ]

Trending Searches
[Action] [Comedy] [Drama] [Thriller]

Browse by Genre
[Action] [Comedy] [Drama]
[Thriller] [Romance] [Sci-Fi]

Top Picks for You
[poster][poster][poster]
```

Search should:

- Have a prominent search field
- Support keyboard input
- Show clear/close action when text exists
- Provide useful empty state
- Show loading state
- Show results without unnecessary navigation
- Preserve existing search functionality

---

# 8. SEARCH RESULTS

When the user searches:

```text
< Back

[ Search query                     ]

All    Movies    Series

┌──────────────────────────────┐
│ poster  Movie Title       ▶  │
│         2014 • Movie         │
└──────────────────────────────┘

┌──────────────────────────────┐
│ poster  Another Title     ▶  │
│         2020 • Series        │
└──────────────────────────────┘
```

Requirements:

- Compact result cards
- Poster thumbnail
- Title
- Metadata
- Quick play button where appropriate
- Tap result opens details
- Proper loading state
- Proper no-results state
- Preserve source/search functionality already implemented

---

# 9. MOVIE DETAILS SCREEN

Create a dedicated movie/series details screen.

Structure:

```text
Hero image
        ↓
Title
Year • Runtime • Type
Rating / metadata
        ↓
[ ▶ Play ]
[ ＋ My List ]

Description

Actions:
Share
Download
More

More Like This
[poster][poster][poster][poster]
```

Requirements:

- Large cinematic header
- Back navigation
- Clear primary Play CTA
- My List functionality if supported by the existing app
- Description
- Metadata
- Related content
- Download action if supported
- Share action if supported
- Source selection should NOT dominate this screen

Do not display fake ratings or metadata if real data does not exist.

---

# 10. VIDEO PLAYER

Preserve the existing playback implementation.

The redesign should focus on the surrounding player UI without breaking playback.

Expected controls:

- Back
- Play/pause
- Seek
- ±10 second controls if supported
- Progress bar
- Duration
- Subtitle/CC control if supported
- Audio/quality control if supported
- Cast control if supported
- More/options menu if supported

The player should:

- Support landscape orientation where the existing player allows it
- Respect system UI behavior
- Avoid accidental touches
- Hide controls automatically after inactivity if the current implementation supports this
- Preserve playback position

Do NOT rewrite the media playback engine unless necessary.

---

# 11. DOWNLOADS SCREEN

Redesign Downloads to match the new visual system.

Structure:

```text
Downloads                         ⚙

[ Movies ] [ Series ]

Downloaded content

┌──────────────────────────────┐
│ poster  Movie Title       ▶  │
│         2014 • 2h 49m        │
│         ✓ Downloaded         │
└──────────────────────────────┘

Storage
━━━━━━━━━━━━━━━━
1.2 GB / 32 GB
```

Requirements:

- Preserve existing download functionality
- Show download progress when downloading
- Show downloaded state
- Allow opening downloaded content
- Allow removal if existing functionality supports it
- Show useful empty state when nothing is downloaded
- Show storage information when available

---

# 12. YOU / PROFILE SCREEN

The existing "You" screen currently contains:

- Preferences
- Dark theme
- Wi-Fi downloads only
- Watch history
- Clear history
- Sources

Keep these capabilities but redesign the screen.

Suggested structure:

```text
You

[ NiceTime profile/avatar ]

Preferences
    Dark theme                         toggle
    Wi-Fi downloads only               toggle
    Watch history                      toggle

History
    Clear history

My Library
    My List
    Continue Watching
    Downloads

App Settings
    Source Settings
    Help & Support
    About
```

The current dark theme should remain the default unless existing user preferences say otherwise.

Do not remove working preferences.

---

# 13. SOURCE SETTINGS

Keep source management available, but move it away from the primary browsing experience.

Example:

```text
< Back

Source Settings

Available Sources

┌─────────────────────────────┐
│ MovieBox               ON   │
│ Primary source              │
└─────────────────────────────┘

┌─────────────────────────────┐
│ 4KHDHub                ON   │
│ Secondary source            │
└─────────────────────────────┘

Additional sources...
```

Important:

- Preserve the existing source architecture.
- Do not invent API integrations.
- Do not hardcode source availability.
- Keep region/availability messaging where the existing implementation requires it.
- Source settings should be understandable to users.

---

# 14. EMPTY STATES

Every major screen needs a deliberate empty state.

Examples:

Downloads:

```text
No downloads yet

Downloaded videos will be available
here for offline viewing.
```

Search:

```text
No results found

Try a different search term.
```

Watch history:

```text
Nothing here yet

Movies and series you watch will
appear here.
```

Use a consistent illustration/icon treatment and typography.

Do not leave large screens visually empty without explanation.

---

# 15. LOADING STATES

Implement polished loading states.

Prefer skeleton placeholders where appropriate instead of a full-screen spinner.

For content rails:

```text
[████] [████] [████] [████]
```

For details:

- Image placeholder
- Title placeholder
- Metadata placeholder
- Description placeholder

Loading states must not cause layout jumping.

---

# 16. ERROR STATES

Create reusable error components.

Example:

```text
Something went wrong

We couldn't load this content.

[ Try again ]
```

Errors should:

- Explain what happened at a high level
- Provide retry when possible
- Avoid exposing raw exceptions
- Preserve navigation

---

# 17. ANIMATIONS

Use subtle animations.

Recommended:

- Bottom-nav selection transition
- Card press feedback
- Screen transitions
- Hero image/content fade
- Loading shimmer where appropriate
- Progress updates
- Download progress

Avoid excessive animations.

Animations must not reduce usability or performance.

---

# 18. REUSABLE COMPONENT ARCHITECTURE

Do NOT implement every screen independently.

Create reusable components appropriate to the project's existing architecture.

Likely reusable components:

```text
NiceTimeTopBar
NiceTimeBottomNavigation
HeroBanner
MoviePosterCard
LandscapeMovieCard
ContentRail
ProgressMovieCard
MovieSearchBar
SearchResultCard
PrimaryButton
SecondaryButton
SectionHeader
EmptyState
ErrorState
LoadingSkeleton
SourceCard
PreferenceRow
DownloadCard
```

Use the project's existing naming conventions if they differ.

---

# 19. DATA AND FUNCTIONALITY

This is a UI redesign, not a request to replace the application architecture.

Before coding:

1. Inspect the project.
2. Identify the current navigation.
3. Identify screens/routes.
4. Identify data models.
5. Identify repositories/services.
6. Identify source providers.
7. Identify playback implementation.
8. Identify download implementation.
9. Identify preferences/storage.
10. Identify existing reusable UI components.

Then map existing functionality into the redesigned screens.

Do not create fake data to hide missing functionality.

Use mock/demo data only if the existing application explicitly uses a demo catalog.

---

# 20. ACCESSIBILITY

Ensure:

- Touch targets are large enough.
- Icons have content descriptions.
- Text has sufficient contrast.
- Important information is not conveyed by color alone.
- Dynamic font scaling does not completely break layouts.
- Buttons have understandable labels.
- Screen readers can navigate major controls.

---

# 21. PERFORMANCE

This is particularly important for a media application.

Avoid:

- Loading huge images at full resolution unnecessarily.
- Repeated network requests caused by recomposition/re-rendering.
- Unnecessary recomputation.
- Nested scroll containers that cause jank.
- Recreating expensive player resources.
- Blocking the main UI thread.

Use appropriate image loading/caching mechanisms already present in the project.

Maintain smooth horizontal scrolling.

---

# 22. RESPONSIVE DESIGN

The UI must work across:

- Small Android phones
- Standard phones
- Large phones
- Different aspect ratios
- Gesture navigation
- Different font scales

Do not design only for the exact screenshot dimensions.

Cards should adapt sensibly.

Hero height should adapt to available screen size.

---

# 23. EXISTING FUNCTIONALITY TO PRESERVE

Before changing anything, identify all currently working functionality.

The redesign must NOT accidentally remove:

- Movie browsing
- Search
- Source selection
- Playback
- Continue watching
- Watch history
- Downloads
- Preferences
- Dark theme
- Wi-Fi-only downloads
- Source toggles
- Existing navigation
- Existing data retrieval
- Existing persistence

If functionality currently exists but the new design does not show it directly, move it into an appropriate secondary screen rather than deleting it.

---

# 24. VISUAL REFERENCE

Use the provided NiceTime Netflix-inspired mockup as the visual reference.

The target design contains these screens:

1. Home
2. Search / Explore
3. Search Results
4. Movie Details
5. Video Player
6. Continue Watching
7. Downloads
8. You / Profile
9. Source Settings
10. Empty / Error States

The mockup is a **design reference**, not a requirement to copy every pixel.

Use it to understand:

- Layout hierarchy
- Card proportions
- Spacing
- Navigation
- Typography scale
- Color direction
- Content density
- Visual hierarchy

---

# 25. IMPLEMENTATION PROCESS

Follow this order.

## Phase 1 — Inspect

Do not immediately start rewriting UI.

First inspect:

- Project structure
- Gradle configuration
- Android version
- UI framework
- Navigation
- Existing screens
- Existing theme
- Existing models
- Existing data layer
- Existing playback
- Existing download functionality

## Phase 2 — Plan

Create a concise implementation plan based on what is actually present in the repository.

Identify:

- Files to modify
- New files/components required
- Existing components to reuse
- Navigation changes
- Theme changes
- Any risks

## Phase 3 — Implement theme/design system

Centralize:

- Colors
- Typography
- Shapes
- Spacing
- Component styling

## Phase 4 — Implement navigation shell

Build:

- Top navigation where required
- Bottom navigation
- Screen transitions
- Edge-to-edge handling

## Phase 5 — Implement screens

Recommended order:

1. Home
2. Search
3. Search results
4. Movie details
5. Player UI
6. Downloads
7. You
8. Source settings
9. Empty/error/loading states

## Phase 6 — Integrate existing functionality

Connect the new UI to the existing data/business logic.

## Phase 7 — Test

Test every navigation path and existing feature.

---

# 26. IMPORTANT CODING RULES

- Do not unnecessarily migrate Compose ↔ XML.
- Do not rewrite working business logic.
- Do not replace dependencies without a reason.
- Do not remove existing features because they are not visible in the mockup.
- Do not hardcode production content.
- Do not hardcode fake progress values.
- Do not add fake APIs.
- Do not introduce unnecessary libraries.
- Follow the project's current architecture.
- Keep components reusable.
- Keep code maintainable.
- Avoid duplicated UI code.
- Avoid giant screen files where reusable components make sense.
- Do not leave debug code in production UI.
- Do not break existing playback/download functionality.

---

# 27. VERIFICATION CHECKLIST

Before considering the task complete, verify:

### Build
- [ ] Project builds successfully.
- [ ] No new compilation errors.
- [ ] No unresolved references.
- [ ] Gradle configuration remains valid.

### Navigation
- [ ] Home works.
- [ ] Search works.
- [ ] Downloads works.
- [ ] You works.
- [ ] Back navigation works.
- [ ] Deep navigation does not create broken states.

### Content
- [ ] Posters load correctly.
- [ ] Hero content loads correctly.
- [ ] Horizontal rails scroll smoothly.
- [ ] Movie details load correctly.
- [ ] Search results work.

### Playback
- [ ] Existing player still launches.
- [ ] Existing playback functionality still works.
- [ ] Watch progress is preserved.

### Downloads
- [ ] Existing download functionality still works.
- [ ] Downloaded content remains accessible.
- [ ] Empty state works.

### Preferences
- [ ] Dark theme works.
- [ ] Wi-Fi-only downloads preference works.
- [ ] Watch history preference works.
- [ ] Clear history still works.
- [ ] Source toggles still work.

### UI
- [ ] Consistent spacing.
- [ ] Consistent typography.
- [ ] Consistent card styling.
- [ ] Consistent buttons.
- [ ] Proper system-bar handling.
- [ ] No clipping.
- [ ] No unexpected horizontal overflow.
- [ ] No major layout jumps.
- [ ] Works on multiple screen sizes.

---

# 28. FINAL EXPECTATION

The final result should feel like:

> "A polished, premium streaming application called NiceTime."

It should NOT feel like:

> "A generic Android demo app with a dark theme."

And it should NOT be:

> "A direct Netflix clone."

The priority order is:

1. Preserve functionality.
2. Improve usability.
3. Create a strong streaming-app information hierarchy.
4. Establish a consistent NiceTime visual identity.
5. Make the UI responsive and performant.
6. Keep the implementation maintainable.

After implementation, summarize:
- What was changed.
- Which existing files/components were reused.
- Which new components were created.
- Any functionality that could not be preserved and why.
- Build/test result.
