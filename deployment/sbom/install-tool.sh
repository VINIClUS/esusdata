#!/usr/bin/env bash
# Installs one of the release-security tools (ADR 0019) into a directory, pinned by version and by
# the SHA-256 of the downloaded file (§1.12.8): no third-party action, no "latest". Linux x86_64
# only, the platform of the jobs that scan and validate.
#
#   deployment/sbom/install-tool.sh trivy|cyclonedx|cargo-cyclonedx <bin-dir>
#
# Bumping a tool means changing its version and hash here, together.
set -euo pipefail

tool="${1:?tool name}"
bin="${2:?bin directory}"
mkdir -p "$bin"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fetch() { # url sha256 file
  curl -sSfL --retry 3 -o "$work/$3" "$1"
  echo "$2  $work/$3" | sha256sum -c --quiet
}

case "$tool" in
  trivy)
    version=0.74.0
    fetch "https://github.com/aquasecurity/trivy/releases/download/v$version/trivy_${version}_Linux-64bit.tar.gz" \
      2ae6fe3ee734b7fdf11335663e18c75ea12dccc76062f09f164a3b0f8be4371a trivy.tar.gz
    tar -xzf "$work/trivy.tar.gz" -C "$bin" trivy
    ;;
  cyclonedx)
    version=0.33.1
    fetch "https://github.com/CycloneDX/cyclonedx-cli/releases/download/v$version/cyclonedx-linux-x64" \
      bfc8b2538da86fe239bc53658bbb63c1c8c510a293c1e6891aa5bea5d3c58746 cyclonedx
    install -m 0755 "$work/cyclonedx" "$bin/cyclonedx"
    ;;
  cargo-cyclonedx)
    version=0.5.9
    fetch "https://github.com/CycloneDX/cyclonedx-rust-cargo/releases/download/cargo-cyclonedx-$version/cargo-cyclonedx-x86_64-unknown-linux-gnu.tar.xz" \
      fb8dbee9f182173e062a64a387b21a0badc6fab8b2abf9294973f012972bf6d8 cargo-cyclonedx.tar.xz
    tar -xJf "$work/cargo-cyclonedx.tar.xz" -C "$work"
    install -m 0755 "$work/cargo-cyclonedx-x86_64-unknown-linux-gnu/cargo-cyclonedx" "$bin/cargo-cyclonedx"
    ;;
  *)
    echo "unknown tool: $tool" >&2
    exit 2
    ;;
esac
