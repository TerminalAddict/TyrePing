#!/usr/bin/env bash
set -euo pipefail

# The PAT triggers downstream release workflows. Never embed it in a remote URL or process argument.
[[ "${GITHUB_REPOSITORY:-}" =~ ^[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9_.-]+$ ]]
[[ "${GITHUB_REPOSITORY#*/}" != '.' && "${GITHUB_REPOSITORY#*/}" != '..' ]]
export GH_TOKEN
GH_TOKEN=$(jq -er '.GITHUB_TOKEN | select(type == "string" and test("^[A-Za-z0-9_]+$"))' secrets/keys.json)
printf '::add-mask::%s\n' "$GH_TOKEN"
printf 'GH_TOKEN=%s\n' "$GH_TOKEN" >> "$GITHUB_ENV"
git remote set-url origin "https://github.com/${GITHUB_REPOSITORY}.git"
git config --local credential.https://github.com.helper ''
git config --local --add credential.https://github.com.helper '!gh auth git-credential'
identity=$(gh api user)
login=$(jq -er '.login' <<< "$identity")
id=$(jq -er '.id' <<< "$identity")
git config user.name "$login"
git config user.email "${id}+${login}@users.noreply.github.com"
