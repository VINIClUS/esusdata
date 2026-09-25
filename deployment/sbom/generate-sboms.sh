#!/usr/bin/env bash
# Writes the release's CycloneDX 1.5 JSON inventories (ADR 0019, Tech Spec §1.12.8), one file per
# part of what ships. Runs after the packaging script, whose build it inventories.
#
#   deployment/sbom/generate-sboms.sh linux|windows <out-dir>
#
# linux:   backend (Maven runtime graph, from the jar build), frontend-runtime (what Vite bundles),
#          frontend-build (the whole npm graph; dev tooling has scope "optional"), execplane-runtime
#          and execplane-build (Cargo graph without/with build dependencies, every target) and
#          runtime-linux (the JDK that jlink cuts the runtime from).
# windows: runtime-windows (the same JDK, plus WinSW as the service wrapper).
#
# Schema 1.5 is the newest one that npm sbom and cargo-cyclonedx emit. Needs jq; linux also needs
# cargo-cyclonedx on PATH and the Node that -Pweb pinned under apps/agent/target/node.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
platform="${1:?linux or windows}"
out="${2:?output directory}"
mkdir -p "$out"

java_home="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"

# The JDK is not a Maven, npm or Cargo package: its document is written here, from the JDK's own
# release file. Extra components (WinSW) arrive as JSON objects on stdin.
runtime_sbom() { # platform
  release_value() { sed -n "s/^$1=\"\(.*\)\"\$/\1/p" "$java_home/release" | tr -d '\r'; }
  local jdk_version implementor
  jdk_version="$(release_value JAVA_RUNTIME_VERSION)"
  implementor="$(release_value IMPLEMENTOR)"
  jq -n --arg os "$1" --arg version "$jdk_version" --arg implementor "$implementor" \
    --arg timestamp "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --slurpfile extra /dev/stdin '{
      bomFormat: "CycloneDX",
      specVersion: "1.5",
      version: 1,
      metadata: {
        timestamp: $timestamp,
        component: {type: "application", name: ("observatorio-aps-runtime-" + $os)}
      },
      components: ([{
        type: "platform",
        "bom-ref": "jdk",
        supplier: {name: $implementor},
        name: "jdk",
        version: $version,
        description: "Runtime cut by jlink from this JDK: every module except jdk.incubator.*",
        licenses: [{license: {id: "GPL-2.0-with-classpath-exception"}}],
        purl: ("pkg:generic/jdk@" + $version + "?os=" + $os)
      }] + $extra)
    }'
}

case "$platform" in
  linux)
    cp "$root/apps/agent/target/classes/META-INF/sbom/application.cdx.json" "$out/backend.cdx.json"

    node_dir="$root/apps/agent/target/node"
    npm=("$node_dir/node" "$node_dir/node_modules/npm/bin/npm-cli.js")
    (cd "$root/apps/web" && "${npm[@]}" sbom --sbom-format cyclonedx --package-lock-only --omit dev) \
      >"$out/frontend-runtime.cdx.json"
    (cd "$root/apps/web" && "${npm[@]}" sbom --sbom-format cyclonedx --package-lock-only) \
      >"$out/frontend-build.cdx.json"

    # cargo-cyclonedx writes next to the manifest; the name must be a bare prefix.
    execplane="$root/apps/execplane"
    (cd "$execplane" && cargo cyclonedx -q --spec-version 1.5 --format json --target all \
      --no-build-deps --override-filename execplane-runtime)
    (cd "$execplane" && cargo cyclonedx -q --spec-version 1.5 --format json --target all \
      --override-filename execplane-build)
    mv "$execplane/execplane-runtime.json" "$out/execplane-runtime.cdx.json"
    mv "$execplane/execplane-build.json" "$out/execplane-build.cdx.json"

    echo '' | runtime_sbom linux >"$out/runtime-linux.cdx.json"
    ;;
  windows)
    # The hash package-windows.ps1 verifies the download against: one source for both.
    ps1="$root/deployment/jpackage/package-windows.ps1"
    winsw_url="$(sed -n "s/^\$winswUrl = '\(.*\)'.*/\1/p" "$ps1" | tr -d '\r')"
    winsw_sha256="$(sed -n "s/^\$winswSha256 = '\(.*\)'.*/\1/p" "$ps1" | tr -d '\r')"
    winsw_version="$(sed -n 's:.*/download/v\([^/]*\)/.*:\1:p' <<<"$winsw_url")"
    if [ -z "$winsw_sha256" ] || [ -z "$winsw_version" ]; then
      echo "could not read the WinSW pin from $ps1" >&2
      exit 1
    fi
    jq -n --arg version "$winsw_version" --arg sha256 "$winsw_sha256" --arg url "$winsw_url" '{
        type: "application",
        "bom-ref": "winsw",
        name: "winsw",
        version: $version,
        description: "Service wrapper, installed as jpackage service-installer.exe",
        hashes: [{alg: "SHA-256", content: $sha256}],
        licenses: [{license: {id: "MIT"}}],
        purl: ("pkg:github/winsw/winsw@v" + $version),
        externalReferences: [{type: "distribution", url: $url}]
      }' | runtime_sbom windows >"$out/runtime-windows.cdx.json"
    ;;
  *)
    echo "unknown platform: $platform" >&2
    exit 2
    ;;
esac
