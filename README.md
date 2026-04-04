# MyLiftSquad

A companion app for powerlifting spectators. Create a squad of athletes you're following at a competition and quickly access their stats from OpenPowerlifting — no more juggling multiple browser tabs.

> **Disclaimer:** MyLiftSquad is an independent app and is not affiliated with, endorsed by, or connected to OpenPowerlifting, OpenIPF, or LiftingCast in any way.

## Features

- Create named groups (squads) of athletes
- Search and add athletes by name from OpenPowerlifting
- View athlete stats at a glance
- Built for spectators at powerlifting competitions

## Project Structure

```
myliftsquad/
├── android/        # Android app (Kotlin, Jetpack Compose)
├── ios/            # iOS app (Swift, SwiftUI)
├── shared/
│   ├── assets/     # Icons, images
│   ├── docs/       # Documentation
│   └── screenshots/# App Store / Play Store screenshots
└── README.md
```

## Package Names

- Android: `com.gooseco.myliftsquad`
- iOS: `com.gooseco.myliftsquad`

## Data Sources

Athlete data is sourced from [OpenPowerlifting](https://www.openpowerlifting.org).

## Development

See the `android/` and `ios/` directories for platform-specific build instructions.
