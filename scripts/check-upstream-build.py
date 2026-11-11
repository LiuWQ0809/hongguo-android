#!/usr/bin/env python3
"""拒绝版本号以外的上游构建配置变更。"""
import re
import subprocess
import sys


def normalized(ref):
    source = subprocess.check_output(
        ["git", "show", f"{ref}:app/build.gradle.kts"], text=True
    )
    return "\n".join(
        line for line in source.splitlines()
        if not re.match(r"\s*(versionCode|versionName)\s*=", line)
    )


if normalized(sys.argv[1]) != normalized(sys.argv[2]):
    sys.exit("上游修改了构建配置，需要人工整合")
