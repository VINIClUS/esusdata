#!/usr/bin/env bash
# Installs the .deb on a real systemd host and walks the package lifecycle (ADR 0014, Tech Spec
# §1.12.5): install, service up as the unprivileged user, restart, reinstall keeping /etc edits,
# remove, purge — data, configuration and the service user must survive the last two.
#
#   sudo deployment/jpackage/test-deb-lifecycle.sh [path/to.deb]
#
# Must run as root and changes the host: use the CI runner or a throwaway VM, never a workstation
# that already has the package installed.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
deb="${1:-$(ls "$here"/../../target/jpackage/*.deb)}"
deb="$(readlink -f "$deb")"
pkg=observatorio-aps
service=observatorio-aps
user=observatorio
data=/var/lib/observatorio-aps
etc=/etc/observatorio-aps
marker="# lifecycle-test $$"

step() { echo "lifecycle: $*"; }
fail() {
  echo "lifecycle: FAIL: $*" >&2
  journalctl -u "$service" --no-pager -n 200 >&2 || true
  exit 1
}

[[ "$(id -u)" -eq 0 ]] || { echo "must run as root" >&2; exit 2; }
if dpkg -s "$pkg" >/dev/null 2>&1; then
  echo "$pkg is already installed; run this on a clean host" >&2
  exit 2
fi

# owner:group mode of a path, e.g. "observatorio:observatorio 700".
perms() { stat -c '%U:%G %a' "$1"; }

expect_perms() {
  local got
  got="$(perms "$1")"
  [[ "$got" = "$2" ]] || fail "$1 is $got, expected $2"
}

# Host must match observatorio.web.allowed-hosts (ENG-49), as in smoke-app-image.sh.
wait_ready() {
  for _ in $(seq 1 90); do
    if curl -fsS -H 'Host: 127.0.0.1:8080' http://127.0.0.1:8080/api/v1/ready >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  fail "/api/v1/ready did not answer within 90s"
}

expect_running_as_user() {
  local pid owner
  systemctl is-active --quiet "$service" || fail "$service is not active"
  pid="$(systemctl show -p MainPID --value "$service")"
  [[ "$pid" -gt 0 ]] || fail "$service has no main process"
  owner="$(ps -o user= -p "$pid" | tr -d ' ')"
  [[ "$owner" = "$user" ]] || fail "$service runs as $owner, expected $user"
}

expect_stopped_cleanly() {
  local result
  systemctl stop "$service"
  result="$(systemctl show -p Result --value "$service")"
  # SuccessExitStatus=143 makes the JVM's SIGTERM exit a clean stop.
  [[ "$result" = success ]] || fail "$service stopped with Result=$result"
}

expect_state_kept() {
  getent passwd "$user" >/dev/null || fail "user $user was removed"
  getent group "$user" >/dev/null || fail "group $user was removed"
  [[ -d "$data" ]] || fail "$data was removed"
  [[ -f "$data/lifecycle-marker" ]] || fail "data in $data was removed"
  grep -qF "$marker" "$etc/application.yml" || fail "$etc/application.yml lost the local edit"
}

step "install $deb"
DEBIAN_FRONTEND=noninteractive apt-get install -y "$deb"

step "postinst created the user and directories"
getent passwd "$user" >/dev/null || fail "no user $user"
[[ "$(getent passwd "$user" | cut -d: -f7)" = /usr/sbin/nologin ]] || fail "$user has a login shell"
expect_perms "$data" "$user:$user 700"
expect_perms "$etc" "root:$user 750"
expect_perms "$etc/application.yml" "root:$user 640"

step "service enabled, active, unprivileged and ready"
systemctl is-enabled --quiet "$service" || fail "$service is not enabled"
wait_ready
expect_running_as_user
# ProtectSystem=strict + ReadWritePaths: the service could create its database here.
[[ -n "$(find "$data" -type f -user "$user" -print -quit)" ]] || fail "service wrote nothing to $data"

step "restart"
systemctl restart "$service"
wait_ready
expect_running_as_user

step "stop and start"
expect_stopped_cleanly
systemctl start "$service"
wait_ready

step "reinstall keeps local configuration and data"
echo "$marker" >>"$etc/application.yml"
touch "$data/lifecycle-marker"
old_pid="$(systemctl show -p MainPID --value "$service")"
DEBIAN_FRONTEND=noninteractive apt-get install -y --reinstall "$deb"
expect_state_kept
expect_perms "$etc/application.yml" "root:$user 640"
# prerm stops the old unit and postinst starts it again: /ready must come from the new JVM.
new_pid="$(systemctl show -p MainPID --value "$service")"
[[ "$new_pid" != "$old_pid" ]] || fail "$service was not restarted by the reinstall"
wait_ready
expect_running_as_user

step "remove keeps data, configuration and user"
DEBIAN_FRONTEND=noninteractive apt-get remove -y "$pkg"
# The alias goes away with disable, so ask about the real unit and look for the JVM itself.
if systemctl is-active --quiet "$pkg-$service"; then fail "$pkg-$service still active after remove"; fi
if pgrep -u "$user" >/dev/null; then fail "processes of $user still running after remove"; fi
[[ ! -e /opt/observatorio-aps ]] || fail "/opt/observatorio-aps left after remove"
[[ ! -e "/lib/systemd/system/$pkg-$service.service" ]] || fail "unit file left after remove"
expect_state_kept

step "purge keeps data, configuration and user"
DEBIAN_FRONTEND=noninteractive apt-get purge -y "$pkg"
if dpkg -s "$pkg" >/dev/null 2>&1; then fail "$pkg still known to dpkg after purge"; fi
expect_state_kept

step "ok"
