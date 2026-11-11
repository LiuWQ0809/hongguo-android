#!/usr/bin/env bash
set -euo pipefail
base=$(cat .github/upstream-base.txt)
latest=${1:?需要上游版本}
output=${2:?需要输出目录}
[[ "$base" =~ ^v[0-9]+(\.[0-9]+){2,3}$ && "$latest" =~ ^v[0-9]+(\.[0-9]+){2,3}$ ]]
mkdir -p "$output"
if [[ "$base" == "$latest" ]]; then echo 'false' > "$output/changed"; exit 0; fi
git fetch --no-tags https://github.com/OOIUa/hongguo-android.git \
  "refs/tags/$base:refs/tags/$base" "refs/tags/$latest:refs/tags/$latest"
git merge-base --is-ancestor "$base" "$latest" || { echo '上游版本不是已知基线的后续版本' >&2; exit 1; }
python3 scripts/check-upstream-build.py "$base" "$latest"
if [[ -n "$(git diff --name-only "$base" "$latest" -- app/src/main/AndroidManifest.xml gradle/ build.gradle.kts settings.gradle.kts)" ]]; then
  echo '上游修改了构建或权限配置，需要人工整合' >&2; exit 1
fi
if [[ -n "$(git diff --name-only "$base" "$latest" -- 'app/src/main/java/com/hongguo/android/Update*.kt')" ]]; then
  echo '上游修改了更新安全逻辑，需要人工整合' >&2; exit 1
fi
paths=(app/src/main/java app/src/main/res app/src/main/assets app/src/test)
lines=$(git diff --numstat "$base" "$latest" -- "${paths[@]}" | awk '
  $1 !~ /^[0-9]+$/ || $2 !~ /^[0-9]+$/ { bad=1 }
  { total += $1 + $2 }
  END { if (bad) exit 1; print total+0 }')
if (( lines == 0 )); then echo 'false' > "$output/changed"; exit 0; fi
if (( lines > 40 )); then echo '上游改动超过单次自动整合上限，需要人工拆分' >&2; exit 1; fi
git diff --binary "$base" "$latest" -- "${paths[@]}" > "$output/upstream.patch"
git apply --3way --index "$output/upstream.patch"
printf '%s\n' "$latest" > .github/upstream-base.txt
python3 scripts/bump-local-version.py
git add .github/upstream-base.txt app/build.gradle.kts
git diff --cached --binary > "$output/candidate.patch"
echo 'true' > "$output/changed"
