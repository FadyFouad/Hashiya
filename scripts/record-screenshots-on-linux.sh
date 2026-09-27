#!/usr/bin/env bash
# Records Roborazzi baselines on GitHub's Linux runners (the source of truth) for the current commit
# and copies them into this checkout. Needs `gh` logged in and the `origin` remote.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
branch="record-screenshots/$(git rev-parse --short HEAD)"

git push --force --quiet origin "HEAD:refs/heads/$branch"
trap 'git push --quiet origin --delete "$branch" || true' EXIT

run_id=""
for _ in $(seq 1 60); do
  run_id=$(gh run list --branch "$branch" --workflow ci.yml --limit 1 --json databaseId --jq '.[0].databaseId // empty')
  [ -n "$run_id" ] && break
  sleep 5
done
[ -n "$run_id" ] || { echo "No CI run started for $branch" >&2; exit 1; }

gh run watch "$run_id" --exit-status --interval 30 > /dev/null

download_dir=$(mktemp -d)
gh run download "$run_id" --name screenshot-baselines --dir "$download_dir"
cp -R "$download_dir"/. .
echo "Baselines copied from run $run_id"
