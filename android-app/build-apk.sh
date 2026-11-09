#!/usr/bin/env bash
# Build the signed BOW BOW release APK. Run from the android-app/ dir:
#   ./build-apk.sh
# Requires: Java 17, Android SDK (platform 34 + build-tools 34.0.0),
# and a release keystore described in keystore.properties (git-ignored;
# see README.md in this folder). The APK lands at:
#   app/build/outputs/apk/release/app-release.apk
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
# Loopback fix (see gradle.properties): Java TCP to 127.0.0.1 is
# intercepted in this sandbox, but Java TCP to ::1 works. The Gradle
# launcher (client) JVM needs the same preference as the daemon.
# NOTE: Gradle does not forward -D properties from org.gradle.jvmargs
# to the daemon, so the flag is also set via JAVA_TOOL_OPTIONS, which
# the JVM itself applies to every java process it starts (client and
# daemon alike).
export GRADLE_OPTS="${GRADLE_OPTS:-} -Djava.net.preferIPv6Addresses=true"
export JAVA_OPTS="${JAVA_OPTS:-} -Djava.net.preferIPv6Addresses=true"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.net.preferIPv6Addresses=true"
mkdir -p "$GRADLE_USER_HOME"

# If this machine reaches the internet through an authenticated proxy
# (https_proxy env), teach Gradle about it. Nothing is written into
# the repo; the proxy password lives only in
# $GRADLE_USER_HOME/gradle.properties (mode 600).
if [ -n "${https_proxy:-}" ]; then
  P="${https_proxy#http://}"; P="${P#https://}"
  USERPASS="${P%%@*}"; HOSTPORT="${P#*@}"
  PUSER="${USERPASS%%:*}"; PPASS="${USERPASS#*:}"
  PHOST="${HOSTPORT%%:*}"; PPORT="${HOSTPORT##*:}"
  PROP_FILE="$GRADLE_USER_HOME/gradle.properties"
  touch "$PROP_FILE"
  add_prop() {
    grep -q "^$1=" "$PROP_FILE" 2>/dev/null || echo "$1=$2" >> "$PROP_FILE"
  }
  add_prop systemProp.http.proxyHost "$PHOST"
  add_prop systemProp.http.proxyPort "$PPORT"
  add_prop systemProp.http.proxyUser "$PUSER"
  add_prop systemProp.http.proxyPassword "$PPASS"
  add_prop systemProp.https.proxyHost "$PHOST"
  add_prop systemProp.https.proxyPort "$PPORT"
  add_prop systemProp.https.proxyUser "$PUSER"
  add_prop systemProp.https.proxyPassword "$PPASS"
  add_prop systemProp.http.nonProxyHosts "localhost"
  add_prop systemProp.https.nonProxyHosts "localhost"
  chmod 600 "$PROP_FILE"
fi

# Prefer the Gradle wrapper when it exists (Android Studio generates it);
# otherwise use a local Gradle 8.7 install.
if [ -x ./gradlew ]; then
  GRADLE_BIN="./gradlew"
elif [ -x "$HOME/workspace/gradle-8.7/bin/gradle" ]; then
  GRADLE_BIN="$HOME/workspace/gradle-8.7/bin/gradle"
else
  GRADLE_BIN="gradle"
fi

"$GRADLE_BIN" --console=plain --no-daemon assembleRelease
echo "APK: app/build/outputs/apk/release/app-release.apk"
