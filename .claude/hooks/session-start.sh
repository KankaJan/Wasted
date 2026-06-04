#!/bin/bash
# SessionStart hook: provision the Android SDK so a Claude Code on the web
# session can build/test this Gradle Android project.
#
# Requirements: outbound network access to dl.google.com (SDK packages) and the
# Google/Maven artifact repositories (AGP, AndroidX, Room/KSP). Choose an
# environment network policy that allows these hosts.
#
# Idempotent and safe to re-run. Remote-only: a no-op on a local machine where
# you already have an SDK / use Android Studio. Network failures are non-fatal —
# the session still starts, just without a provisioned SDK (with a warning).
set -uo pipefail

# Only run inside Claude Code on the web; locally, use your own SDK.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  echo "session-start: not a remote session, skipping Android SDK provisioning."
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"   # cmdline-tools 12.0
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

# Pin these to the project's build.gradle.kts (compileSdk / targetSdk = 35).
PLATFORM="platforms;android-35"
BUILD_TOOLS="build-tools;35.0.0"
PLATFORM_TOOLS="platform-tools"

SDKMANAGER="${SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"

# Warn but let the session start anyway (e.g. restricted network policy).
soft_fail() {
  echo "session-start: WARNING: $1" >&2
  echo "session-start: SDK not provisioned. The next build will need an environment" >&2
  echo "               network policy that allows dl.google.com and the Maven/Google" >&2
  echo "               artifact repositories. See HANDOVER.md section 4." >&2
  exit 0
}

echo "session-start: provisioning Android SDK at ${SDK_ROOT}"
mkdir -p "${SDK_ROOT}"

# 1. Install command-line tools if missing.
if [ ! -x "${SDKMANAGER}" ]; then
  echo "session-start: downloading Android command-line tools..."
  TMP_ZIP="$(mktemp --suffix=.zip)"
  curl -fSL "${CMDLINE_TOOLS_URL}" -o "${TMP_ZIP}" \
    || soft_fail "could not download command-line tools from dl.google.com"
  rm -rf "${SDK_ROOT}/cmdline-tools/latest" "${SDK_ROOT}/cmdline-tools/temp"
  mkdir -p "${SDK_ROOT}/cmdline-tools/temp"
  unzip -q "${TMP_ZIP}" -d "${SDK_ROOT}/cmdline-tools/temp" \
    || soft_fail "could not unzip command-line tools"
  # The zip extracts to a top-level "cmdline-tools" dir; Android expects it at
  # cmdline-tools/latest.
  mkdir -p "${SDK_ROOT}/cmdline-tools/latest"
  mv "${SDK_ROOT}/cmdline-tools/temp/cmdline-tools/"* "${SDK_ROOT}/cmdline-tools/latest/"
  rm -rf "${SDK_ROOT}/cmdline-tools/temp" "${TMP_ZIP}"
else
  echo "session-start: command-line tools already present."
fi

export ANDROID_HOME="${SDK_ROOT}"
export ANDROID_SDK_ROOT="${SDK_ROOT}"
export PATH="${SDK_ROOT}/cmdline-tools/latest/bin:${SDK_ROOT}/platform-tools:${PATH}"

# 2. Accept licenses (idempotent) and install the required packages.
echo "session-start: accepting licenses..."
yes | "${SDKMANAGER}" --licenses >/dev/null 2>&1 || true

echo "session-start: installing SDK packages..."
"${SDKMANAGER}" --install "${PLATFORM_TOOLS}" "${PLATFORM}" "${BUILD_TOOLS}" \
  || soft_fail "could not install SDK packages"

# 3. Point Gradle at the SDK.
echo "sdk.dir=${SDK_ROOT}" > "${PROJECT_DIR}/local.properties"

# 4. Persist environment variables for the rest of the session.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=\"${SDK_ROOT}\""
    echo "export ANDROID_SDK_ROOT=\"${SDK_ROOT}\""
    echo "export PATH=\"${SDK_ROOT}/cmdline-tools/latest/bin:${SDK_ROOT}/platform-tools:\$PATH\""
  } >> "${CLAUDE_ENV_FILE}"
fi

# 5. Pre-warm the Gradle wrapper distribution (downloads Gradle, not deps).
if [ -x "${PROJECT_DIR}/gradlew" ]; then
  echo "session-start: pre-warming Gradle wrapper..."
  ( cd "${PROJECT_DIR}" && ./gradlew --version >/dev/null 2>&1 ) || true
fi

echo "session-start: Android SDK ready."
