#!/usr/bin/env bash
#
# generate-keystore.sh — one-time pan-tree release keystore generator
#
# Run from Git Bash on the signing machine. Produces:
#   android/pantrie-release.keystore
#
# CRITICAL: this keystore is the ONLY thing that can sign updates to the
# Play Store listing for app.pantrie. If you lose it, you lose the listing.
# Back up the .keystore file AND the passwords to a password manager AND a
# cloud drive before shipping the first release.
#
set -euo pipefail

# Resolve repo root (scripts/ -> ..)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
KEYSTORE_DIR="${REPO_ROOT}/android"
KEYSTORE_PATH="${KEYSTORE_DIR}/pantrie-release.keystore"
ALIAS="pantrie"

echo "==> pan-tree release keystore generator"
echo "    target: ${KEYSTORE_PATH}"
echo

# 1. Refuse to overwrite — overwriting would brick every future Play Store update.
if [ -e "${KEYSTORE_PATH}" ]; then
  echo "ERROR: ${KEYSTORE_PATH} already exists."
  echo "       Refusing to overwrite — replacing the keystore would permanently"
  echo "       brick updates for the app.pantrie Play Store listing."
  echo "       If you are certain you want to regenerate, move or rename the"
  echo "       existing file manually first."
  exit 1
fi

# 2. Sanity-check keytool availability.
if ! command -v keytool >/dev/null 2>&1; then
  echo "ERROR: 'keytool' not found on PATH."
  echo "       keytool ships with the JDK. On this machine you can use:"
  echo "         export PATH=\"/c/Program Files/Android/Android Studio/jbr/bin:\$PATH\""
  echo "       then re-run this script."
  exit 1
fi

# 3. Confirm gitignore coverage so we don't accidentally commit secrets.
if ! grep -qE '(^|/)\*\.keystore' "${REPO_ROOT}/.gitignore" 2>/dev/null; then
  echo "WARNING: root .gitignore does not match *.keystore — verify before committing."
fi

mkdir -p "${KEYSTORE_DIR}"

# 4. Interactive prompts. Passwords read silently.
read -rsp "Keystore password (min 6 chars, SAVE THIS): " STORE_PW
echo
read -rsp "Confirm keystore password: " STORE_PW2
echo
if [ "${STORE_PW}" != "${STORE_PW2}" ]; then
  echo "ERROR: keystore passwords do not match."; exit 1
fi
if [ "${#STORE_PW}" -lt 6 ]; then
  echo "ERROR: keystore password must be at least 6 characters."; exit 1
fi

read -rsp "Key password for alias '${ALIAS}' (press Enter to reuse keystore pw): " KEY_PW
echo
if [ -z "${KEY_PW}" ]; then
  KEY_PW="${STORE_PW}"
else
  read -rsp "Confirm key password: " KEY_PW2
  echo
  if [ "${KEY_PW}" != "${KEY_PW2}" ]; then
    echo "ERROR: key passwords do not match."; exit 1
  fi
fi

echo
echo "The following identity is embedded in the certificate. Use your real name"
echo "and legal org — Play Store displays the cert fingerprint to users."
read -rp "Full name (CN)            [Kyle Schulgen]: " CN
CN="${CN:-Kyle Schulgen}"
read -rp "Organizational unit (OU)  [pan-tree]: " OU
OU="${OU:-pan-tree}"
read -rp "Organization (O)          [pan-tree]: " O
O="${O:-pan-tree}"
read -rp "City / locality (L)       [ ]: " L
read -rp "State / province (ST)     [ ]: " ST
read -rp "Country code (C, 2 letter)[US]: " C
C="${C:-US}"

DNAME="CN=${CN}, OU=${OU}, O=${O}, L=${L}, ST=${ST}, C=${C}"

echo
echo "==> Generating 2048-bit RSA key, 10000-day validity, alias='${ALIAS}'"
echo

# 5. Generate.
keytool -genkey -v \
  -keystore "${KEYSTORE_PATH}" \
  -alias "${ALIAS}" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "${STORE_PW}" \
  -keypass "${KEY_PW}" \
  -dname "${DNAME}"

# 6. Report + reminders.
echo
echo "==> SUCCESS"
echo "    Keystore:  ${KEYSTORE_PATH}"
echo "    Alias:     ${ALIAS}"
echo
echo "BACKUP CHECKLIST (do this NOW, before shipping):"
echo "  [ ] Copy ${KEYSTORE_PATH} to a password manager secure file AND a"
echo "      cloud drive (Google Drive / 1Password / Bitwarden attachment)."
echo "  [ ] Save the keystore password + key password in the same vault."
echo "  [ ] Verify .gitignore excludes *.keystore (it does in this repo)."
echo "  [ ] NEVER commit, email, or paste these into a chat."
echo
echo "If this keystore is lost, the Play Store listing for app.pantrie cannot"
echo "be updated — you will have to publish a brand-new listing under a new"
echo "package id. There is no recovery."
echo
echo "Next: see docs/play-console-setup.md step 2 to build the signed AAB."
