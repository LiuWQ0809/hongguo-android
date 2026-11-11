#!/usr/bin/env python3
"""递增本地版，确保 Android 覆盖更新可识别。"""
from pathlib import Path
import re

path = Path("app/build.gradle.kts")
source = path.read_text(encoding="utf-8")
source, codes = re.subn(
    r'(versionCode\s*=.*?\.orElse\(")(\d+)("\))',
    lambda match: match[1] + str(int(match[2]) + 1) + match[3], source,
)
source, names = re.subn(
    r'(versionName\s*=.*?\.orElse\("\d+\.\d+\.\d+-local\.)(\d+)("\))',
    lambda match: match[1] + str(int(match[2]) + 1) + match[3], source,
)
if (codes, names) != (1, 1):
    raise SystemExit("无法唯一确定本地版本，已停止自动更新")
path.write_text(source, encoding="utf-8")
