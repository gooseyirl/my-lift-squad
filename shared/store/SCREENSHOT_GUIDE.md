# Screenshot Guide — My Lift Squad

## Play Store Requirements
- Format: PNG or JPEG
- Min dimension: 320px, Max: 3840px
- Aspect ratio: between 9:16 and 16:9
- Recommended: **1080 × 1920px** (portrait)
- Minimum: 2 screenshots, maximum: 8

## Recommended Screenshots (in order)

### 1. Home screen with squads and favourites
- Add 2-3 squads with athletes
- Favourite 2-3 athletes so they appear at the top
- Shows the home screen value prop immediately

### 2. Athlete detail sheet
- Tap a favourite or squad athlete to open the bottom sheet
- Scroll down slightly so you can see both stats and the start of competition history
- Shows the core feature

### 3. Squad detail screen
- Open a squad with 4-5 athletes
- Shows athlete cards with SBD PRs, federation, weight class

### 4. Competition history
- Open an athlete detail sheet
- Scroll to show competition history entries
- Shows the depth of data available

### 5. Settings — Backup & Restore
- Open Settings from the speed dial
- Shows the backup/restore options

### 6. Speed dial FAB open
- On the home screen, tap the hamburger FAB to open the speed dial
- Shows New Squad / Settings / Support Developer options

## Capture Script
Use `take-screenshots.sh` in the project root. It uses ADB to capture the screen automatically at each step.

## Feature Graphic
- Size: **1024 × 500px**
- Already generated: `shared/assets/myliftsquad-playstore-feature.png`

## App Icon
- Already generated: `shared/assets/myliftsquad-icon-playstore.png` (512 × 512px)
