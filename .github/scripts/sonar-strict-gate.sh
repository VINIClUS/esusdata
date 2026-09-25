#!/usr/bin/env bash
# The stricter half of the SonarQube Cloud gate (ADR 0019). The Free plan only runs "Sonar way",
# which judges new code; this adds, for the analysis that just finished:
#   - no open issue at all, of any type or severity, on the pull request or the branch;
#   - no issue closed from the UI as accepted or false positive, since exceptions live in the
#     code (@SuppressWarnings("java:Sxxxx") with the reason next to it, as in ADR 0018);
#   - no security hotspot left to review, or marked safe from the UI.
#
#   SONAR_TOKEN=... .github/scripts/sonar-strict-gate.sh pr <number> | branch <name>
set -euo pipefail

: "${SONAR_TOKEN:?SONAR_TOKEN is required}"
kind="${1:?pr or branch}"
ref="${2:?pull request number or branch name}"
api=https://sonarcloud.io/api
project=VINIClUS_esusdata

case "$kind" in
  pr) scope="pullRequest=$ref" ;;
  branch) scope="branch=$ref" ;;
  *) echo "unknown kind: $kind" >&2; exit 2 ;;
esac

get() { curl -sSf --retry 3 -u "$SONAR_TOKEN:" "$api/$1"; }

failed=0
report() { # description json total-jq list-jq
  local total
  total="$(jq -r "$3" <<<"$2")"
  if [[ "$total" != 0 ]]; then
    echo "::error::$1: $total"
    jq -r "$4" <<<"$2"
    failed=1
  else
    echo "$1: 0"
  fi
}

# Each response is fetched into a variable first, so a failed request stops the script (set -e)
# instead of reading as an empty, passing result.
issue_list='.issues[] | "  \(.rule) \(.component | sub("^[^:]*:"; "")):\(.line // "-") \(.message)"'
json="$(get "issues/search?componentKeys=$project&$scope&resolved=false&ps=100")"
report "open issues" "$json" '.total' "$issue_list"
json="$(get "issues/search?componentKeys=$project&$scope&resolutions=WONTFIX,FALSE-POSITIVE&ps=100")"
report "issues accepted or marked false positive in the UI" "$json" '.total' "$issue_list"

hotspot_list='.hotspots[] | "  \(.ruleKey) \(.component | sub("^[^:]*:"; "")):\(.line // "-") \(.message)"'
json="$(get "hotspots/search?projectKey=$project&$scope&status=TO_REVIEW&ps=100")"
report "security hotspots to review" "$json" '.paging.total' "$hotspot_list"
json="$(get "hotspots/search?projectKey=$project&$scope&status=REVIEWED&resolution=SAFE&ps=100")"
report "security hotspots marked safe in the UI" "$json" '.paging.total' "$hotspot_list"

exit "$failed"
