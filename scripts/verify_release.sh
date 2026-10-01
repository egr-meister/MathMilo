#!/usr/bin/env bash
# Verifies signed release artifacts. Usage:
#   scripts/verify_release.sh <apk> <aab> <keystore.p12> <alias> [report-file]
# Requires ANDROID_KEYSTORE_PASSWORD in the environment and ANDROID_HOME with build-tools 36.0.0.
# Never prints passwords.
set -euo pipefail
APK="$1"; AAB="$2"; KS="$3"; ALIAS="$4"; REPORT="${5:-verification-report.txt}"
BT="${ANDROID_HOME:?ANDROID_HOME not set}/build-tools/${BUILD_TOOLS_VERSION:-36.0.0}"
: > "$REPORT"
log() { echo "$*" | tee -a "$REPORT"; }

log "== APK signature (apksigner verify --print-certs) =="
"$BT/apksigner" verify --verbose --print-certs "$APK" | tee -a "$REPORT" > /tmp/apksigner.txt
if grep -q "CN=Android Debug" /tmp/apksigner.txt; then log "FAIL: APK is debug-signed"; exit 1; fi
APK_SHA=$(grep -m1 "certificate SHA-256 digest" /tmp/apksigner.txt | awk '{print $NF}' | tr '[:lower:]' '[:upper:]')

log "== Expected release certificate =="
EXPECTED_SHA=$(keytool -list -v -storetype PKCS12 -keystore "$KS" -alias "$ALIAS" -storepass:env ANDROID_KEYSTORE_PASSWORD \
  | grep -m1 "SHA256:" | awk '{print $2}' | tr -d ':' | tr '[:lower:]' '[:upper:]')
log "expected SHA-256: $EXPECTED_SHA"
log "apk signer SHA-256: $APK_SHA"
[ -n "$EXPECTED_SHA" ] && [ "$APK_SHA" = "$EXPECTED_SHA" ] || { log "FAIL: APK signer does not match the release key"; exit 1; }

log "== AAB signature (jarsigner) =="
JS_OUT=$(jarsigner -verify -certs "$AAB" 2>&1 || true)
echo "$JS_OUT" | grep -E "jar verified|jar is unsigned|WARNING|Error" | tee -a "$REPORT" || true
echo "$JS_OUT" | grep -q "jar verified." || { log "FAIL: AAB signature did not verify"; exit 1; }
echo "$JS_OUT" | grep -q "jar is unsigned" && { log "FAIL: AAB is unsigned"; exit 1; }
AAB_CERT=$(keytool -printcert -jarfile "$AAB")
if echo "$AAB_CERT" | grep -q "CN=Android Debug"; then log "FAIL: AAB is debug-signed"; exit 1; fi
AAB_SHA=$(echo "$AAB_CERT" | grep -m1 "SHA256:" | awk '{print $2}' | tr -d ':' | tr '[:lower:]' '[:upper:]')
log "aab signer SHA-256: $AAB_SHA"
[ "$AAB_SHA" = "$EXPECTED_SHA" ] || { log "FAIL: AAB signer does not match the release key"; exit 1; }
log "(A self-signed upload certificate is expected and accepted.)"

log "== Packaged permissions (aapt2 dump permissions) =="
PERMS=$("$BT/aapt2" dump permissions "$APK")
echo "$PERMS" | tee -a "$REPORT"
# androidx.core adds a signature-level <app>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION; it is not a runtime permission.
UNEXPECTED=$(echo "$PERMS" | grep -E "^(uses-permission|permission):" | grep -v "DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" || true)
if [ -n "$UNEXPECTED" ]; then log "FAIL: unexpected permissions:"; log "$UNEXPECTED"; exit 1; fi
log "OK: no INTERNET, ACCESS_NETWORK_STATE or other permissions"

log "== Native libraries / 16 KB page size =="
python3 "$(dirname "$0")/check_elf_alignment.py" "$APK" "$AAB" | tee -a "$REPORT"
"$BT/zipalign" -c -P 16 -v 4 "$APK" > /tmp/zipalign.txt && log "OK: zipalign -c -P 16 passed" || { log "FAIL: zipalign -P 16 check"; exit 1; }

log "ALL RELEASE CHECKS PASSED"
