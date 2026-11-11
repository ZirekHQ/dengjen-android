#!/usr/bin/env bash
# Downloads the dengjen core and tts AARs pinned in dengjen.lock into libs/ and verifies them.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
lock="$root/dengjen.lock"
value() { sed -n "s/^$1=//p" "$lock"; }
version="$(value version)"
[ -n "$version" ] || { echo "error: dengjen.lock has no version" >&2; exit 1; }
mkdir -p "$root/libs"
for module in core tts; do
  expected="$(value "$module")"
  [ -n "$expected" ] || { echo "error: dengjen.lock has no $module digest" >&2; exit 1; }
  out="$root/libs/dengjen-android-$module.aar"
  if [ -f "$out" ] && echo "$expected  $out" | sha256sum --check --status; then
    continue
  fi
  url="https://github.com/ZirekHQ/dengjen/releases/download/v$version/dengjen-android-$module-$version.aar"
  curl --fail --silent --show-error --location --retry 3 --output "$out.tmp" "$url"
  if ! echo "$expected  $out.tmp" | sha256sum --check --status; then
    rm -f "$out.tmp"
    echo "error: $url does not match the digest in dengjen.lock" >&2
    exit 1
  fi
  mv "$out.tmp" "$out"
done
echo "dengjen $version AARs are in libs/"
