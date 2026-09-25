#!/usr/bin/env bash
# Fails unless the JDK that jlink cuts the runtime from is the newest Temurin release of its
# feature version (ADR 0021, Tech Spec §1.12.8). Trivy cannot match the JDK (pkg:generic in the
# runtime SBOM) against CVEs; every OpenJDK CVE fix ships in a quarterly update, so "newest update"
# is the check that stands in for it. A proxy, not a CVE match.
#
#   deployment/sbom/check-jdk-current.sh linux|windows
#
# Reads $JAVA_HOME/release (or the java on PATH). Needs curl and jq.
set -euo pipefail

os="${1:?linux or windows}"
java_home="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"

release_value() { tr -d '\r' <"$java_home/release" | sed -n "s/^$1=\"\(.*\)\"\$/\1/p"; }
installed="$(release_value JAVA_RUNTIME_VERSION)"
implementor="$(release_value IMPLEMENTOR)"
if [[ -z "$installed" ]]; then
  echo "no JAVA_RUNTIME_VERSION in $java_home/release" >&2
  exit 1
fi
if [[ "$implementor" != "Eclipse Adoptium" ]]; then
  echo "expected a Temurin JDK, found '$implementor' in $java_home" >&2
  exit 1
fi
feature="${installed%%.*}"

latest="$(curl -sSf --retry 3 --retry-all-errors \
  "https://api.adoptium.net/v3/assets/latest/$feature/hotspot?os=$os&architecture=x64&image_type=jdk" \
  | jq -er '.[0].version.openjdk_version')"

# Both sides use the JDK's own format, 21.0.12+7-LTS or 21.0.12.1+1-LTS: compare the numbers
# before "+" (padded to four) and then the build number after it.
numbers() {
  local version="${1%%-*}" base build
  base="${version%%+*}"
  build="${version#*+}"
  [[ "$build" == "$version" ]] && build=0
  IFS=. read -r a b c d <<<"$base"
  printf '%d %d %d %d %d\n' "$a" "${b:-0}" "${c:-0}" "${d:-0}" "$build"
}
read -ra have <<<"$(numbers "$installed")"
read -ra want <<<"$(numbers "$latest")"
for i in 0 1 2 3 4; do
  if ((have[i] > want[i])); then break; fi
  if ((have[i] < want[i])); then
    echo "JDK $installed is behind Temurin $latest ($os): update the JDK before packaging" >&2
    exit 1
  fi
done
echo "JDK $installed is current (Temurin $latest, $os)"
