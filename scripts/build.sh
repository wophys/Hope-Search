#!/usr/bin/env bash
# Сборка HOPE.apk в GitHub Codespaces (Ubuntu + JDK 17).
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
GRADLE_VER=8.9

if ! command -v java >/dev/null; then echo "Нужен JDK 17: sudo apt-get install -y openjdk-17-jdk"; exit 1; fi

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  curl -sL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmd.zip
  unzip -q -o /tmp/cmd.zip -d "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platforms;android-34" "build-tools;34.0.0" "platform-tools"

if [ ! -x "$HOME/gradle-$GRADLE_VER/bin/gradle" ]; then
  curl -sL "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip" -o /tmp/gradle.zip
  unzip -q -o /tmp/gradle.zip -d "$HOME"
fi

echo "sdk.dir=$SDK" > local.properties
"$HOME/gradle-$GRADLE_VER/bin/gradle" --no-daemon assembleRelease
cp app/build/outputs/apk/release/app-release.apk HOPE.apk
echo "Готово: $(pwd)/HOPE.apk"
