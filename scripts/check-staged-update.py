#!/usr/bin/env python3
"""发布前校验候选只改动允许的源码与本地递增版本。"""
import re
import subprocess
import sys
from pathlib import Path


def git(*args):
    return subprocess.check_output(["git", *args], text=True)


paths = git("diff", "--cached", "--name-only").splitlines()
prefixes = ("app/src/main/java/", "app/src/main/res/", "app/src/main/assets/", "app/src/test/")
for path in paths:
    if not (path.startswith(prefixes) or path in ("app/build.gradle.kts", ".github/upstream-base.txt")):
        sys.exit("候选包含不允许自动修改的路径")
    if re.fullmatch(r"app/src/main/java/com/hongguo/android/Update[^/]*\.kt", path):
        sys.exit("候选修改了更新安全逻辑")
counts = [line.split("\t", 2)[:2] for line in git("diff", "--cached", "--numstat").splitlines()]
if any(not a.isdigit() or not b.isdigit() for a, b in counts) or sum(int(a) + int(b) for a, b in counts) > 50:
    sys.exit("候选含二进制文件或超过单次提交上限")
if Path(".github/upstream-base.txt").read_text().strip() != sys.argv[1]:
    sys.exit("候选上游版本不匹配")
old, new = git("show", "HEAD:app/build.gradle.kts"), git("show", ":app/build.gradle.kts")
patterns = [r'(versionCode\s*=.*?\.orElse\(")(\d+)("\))',
            r'(versionName\s*=.*?\.orElse\("\d+\.\d+\.\d+-local\.)(\d+)("\))']
for pattern in patterns:
    before, after = list(re.finditer(pattern, old)), list(re.finditer(pattern, new))
    if len(before) != 1 or len(after) != 1 or int(after[0][2]) != int(before[0][2]) + 1:
        sys.exit("候选版本号没有按规则递增")
    old, new = re.sub(pattern, r'\1VERSION\3', old), re.sub(pattern, r'\1VERSION\3', new)
if old != new:
    sys.exit("候选修改了版本号之外的构建配置")
print("候选路径、版本及提交行数校验通过")
