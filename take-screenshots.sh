#!/bin/bash
# Screenshot capture script for My Lift Squad
# Saves screenshots to shared/screenshots/

set -e

SCREENSHOTS_DIR="shared/screenshots"
mkdir -p "$SCREENSHOTS_DIR"

# Find ADB
ADB=""
for path in \
  "$HOME/Library/Android/sdk/platform-tools/adb" \
  "$HOME/Android/Sdk/platform-tools/adb" \
  "/usr/local/bin/adb" \
  "adb"; do
  if command -v "$path" &>/dev/null || [ -x "$path" ]; then
    ADB="$path"
    break
  fi
done

if [ -z "$ADB" ]; then
  echo "❌ ADB not found. Install Android SDK platform-tools."
  exit 1
fi

# Check device connected
DEVICES=$("$ADB" devices | grep -v "List of devices" | grep "device$" | wc -l | tr -d ' ')
if [ "$DEVICES" -eq 0 ]; then
  echo "❌ No device connected. Connect a device or start an emulator."
  exit 1
fi

echo "✅ ADB found: $ADB"
echo "✅ Device connected"
echo ""
echo "📸 My Lift Squad — Screenshot Capture"
echo "======================================"
echo "Screenshots will be saved to: $SCREENSHOTS_DIR/"
echo ""

capture() {
  local name=$1
  local filename="$SCREENSHOTS_DIR/$name.png"
  "$ADB" exec-out screencap -p > "$filename"
  echo "  ✅ Saved: $filename"
}

prompt() {
  local step=$1
  local title=$2
  local instructions=$3
  echo ""
  echo "[$step/6] $title"
  echo "  $instructions"
  echo -n "  Press Enter when ready..."
  read -r
}

# ── Screenshot 1 ──
prompt "1" "Home screen — Squads & Favourites" \
  "Make sure you have 2-3 squads and 2-3 favourited athletes visible on the home screen."
capture "01-home-screen"

# ── Screenshot 2 ──
prompt "2" "Athlete detail sheet" \
  "Tap a favourite or squad athlete to open their detail sheet. Scroll slightly to show stats and competition history."
capture "02-athlete-detail"

# ── Screenshot 3 ──
prompt "3" "Squad detail screen" \
  "Navigate into a squad with 4-5 athletes. Show the full athlete card list."
capture "03-squad-detail"

# ── Screenshot 4 ──
prompt "4" "Competition history" \
  "Open an athlete detail sheet and scroll down to show their competition history entries."
capture "04-competition-history"

# ── Screenshot 5 ──
prompt "5" "Settings — Backup & Restore" \
  "Open the speed dial FAB, tap Settings. Show the Backup & Restore options."
capture "05-settings"

# ── Screenshot 6 ──
prompt "6" "Speed dial FAB open" \
  "Go back to the home screen. Tap the hamburger FAB to open the speed dial menu."
capture "06-speed-dial"

echo ""
echo "======================================"
echo "✅ All 6 screenshots saved to $SCREENSHOTS_DIR/"
echo ""
echo "Upload order for Play Store:"
ls -1 "$SCREENSHOTS_DIR"/*.png 2>/dev/null
