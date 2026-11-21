#!/usr/bin/env bash
# Сборка Hope.apk в GitHub Codespaces (или на любой Linux-машине с интернетом).
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
GRADLE_VER=8.9
GRADLE_HOME="$HOME/gradle-$GRADLE_VER"

# 1. Java 17+
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -qE 'version "(1[7-9]|2[0-9])'; then
  echo ">> Ставлю JDK 17"
  sudo apt-get update -y && sudo apt-get install -y openjdk-17-jdk unzip
fi

# 2. Android SDK
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo ">> Скачиваю Android command-line tools"
  mkdir -p "$SDK/cmdline-tools"
  curl -fsSL -o /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  rm -rf /tmp/cmdtools && unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools
  mv /tmp/cmdtools/cmdline-tools "$SDK/cmdline-tools/latest"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# 3. Gradle
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  echo ">> Скачиваю Gradle $GRADLE_VER"
  curl -fsSL -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip"
  unzip -q /tmp/gradle.zip -d "$HOME"
fi

# 4. Сборка
echo "sdk.dir=$SDK" > local.properties
export ANDROID_HOME="$SDK"
"$GRADLE_HOME/bin/gradle" --no-daemon assembleDebug

mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/Hope.apk
echo
echo "Готово: $(pwd)/dist/Hope.apk"
