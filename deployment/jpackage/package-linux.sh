#!/usr/bin/env bash
# Builds the Linux app image and .deb (ADR 0014, Tech Spec §1.12.5). Must run on Linux with a
# JDK 21 that ships jmods (Temurin does), cargo, dpkg-deb and fakeroot.
#
#   deployment/jpackage/package-linux.sh [--skip-tests]
#
# Output: target/jpackage/ at the repository root.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
out="$root/target/jpackage"
name=observatorio-aps
# jpackage wraps this as "<vendor> <email>" in the control file.
maintainer="${DEB_MAINTAINER:-observatorio-aps@localhost}"

skip_tests=false
for arg in "$@"; do
  case "$arg" in
    --skip-tests) skip_tests=true ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

java_home="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
if [ ! -d "$java_home/jmods" ]; then
  echo "JDK at $java_home has no jmods/ — jlink needs a full JDK" >&2
  exit 1
fi

# 1. Execution plane (ADR 0010, ADR 0011) first, so the test run below exercises the exact
#    binary that gets packaged.
cargo build --release --locked --manifest-path "$root/apps/execplane/Cargo.toml"
execplane="$root/apps/execplane/target/release/observatorio-execplane"

# 2. Backend jar, with the web client under static/ (-Pweb). The surefire flag is the reuseForks
#    quirk documented in README.md; the binary property turns on ExecPlaneDifferentialLiveTest
#    (JDBC vs. Rust on the same fixture).
mvn_args=(-B -f "$root/apps/agent/pom.xml")
if $skip_tests; then
  mvn "${mvn_args[@]}" -Pweb package -DskipTests
else
  mvn "${mvn_args[@]}" -Pweb verify -Dsurefire.reuseForks=false \
    -Dobservatorio.execution-plane.binary="$execplane"
fi
version="$(mvn "${mvn_args[@]}" -q help:evaluate -Dexpression=project.version -DforceStdout)"
jar="esusdata-agent-$version.jar"

# 3. Staging: everything in input/ lands in $APPDIR (lib/app/).
rm -rf "$out"
mkdir -p "$out/input"
cp "$root/apps/agent/target/$jar" "$out/input/"
cp "$execplane" "$out/input/"
cp "$here/linux/observatorio-aps.yml" "$out/input/"

# 4. Runtime. §1.12.5: module reduction comes after the smoke test, so link every JDK module
#    except the incubators.
modules="$(ls "$java_home/jmods" | sed -n 's/\.jmod$//p' | grep -v '^jdk\.incubator\.' | paste -sd, -)"
"$java_home/bin/jlink" --add-modules "$modules" \
  --strip-debug --no-header-files --no-man-pages --strip-native-commands \
  --output "$out/runtime"

# 5. App image, then the .deb built from it.
"$java_home/bin/jpackage" --type app-image \
  --name "$name" \
  --app-version "$version" \
  --vendor "Observatorio APS" \
  --description "Observatorio APS — servico local de indicadores sobre o PEC e-SUS" \
  --input "$out/input" \
  --main-jar "$jar" \
  --runtime-image "$out/runtime" \
  --java-options '-Dobservatorio.install-dir=$APPDIR' \
  --java-options '-Dspring.config.additional-location=optional:file:$APPDIR/observatorio-aps.yml,optional:file:/etc/observatorio-aps/' \
  --dest "$out"

"$java_home/bin/jpackage" --type deb \
  --name "$name" \
  --app-version "$version" \
  --vendor "Observatorio APS" \
  --description "Observatorio APS — servico local de indicadores sobre o PEC e-SUS" \
  --app-image "$out/$name" \
  --install-dir /opt \
  --launcher-as-service \
  --resource-dir "$here/linux/resources" \
  --linux-package-name "$name" \
  --linux-deb-maintainer "$maintainer" \
  --linux-app-category misc \
  --dest "$out"

# The .deb is built from the app image; fail if that ever stops carrying the service (unit file
# plus the jpackage-generated register_services call in postinst).
deb="$(ls "$out"/*.deb)"
unit="./lib/systemd/system/$name-$name.service"
# (Captured first: grep -q closing the pipe early would trip pipefail.)
contents="$(dpkg-deb -c "$deb")"
postinst="$(dpkg-deb -I "$deb" postinst)"
grep -qF "$unit" <<<"$contents" || { echo "no systemd unit in $deb" >&2; exit 1; }
grep -qF "register_services '/lib/systemd/system/$name-$name.service'" <<<"$postinst" \
  || { echo "postinst in $deb does not register the service" >&2; exit 1; }

ls -l "$deb"
