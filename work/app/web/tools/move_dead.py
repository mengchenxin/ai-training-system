# -*- coding: utf-8 -*-
"""把不可达源码移出 src，而不是删除。

移动目标: .backup-src-20261017/_removed/
- 同盘 rename，不产生文件销毁，随时可整体还原
- 只移动 .ts/.tsx/.js/.mjs/.vue，且路径必须在 src/ 之下
"""
import io
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
SRC = os.path.join(ROOT, "src")
DEST = os.path.join(ROOT, ".backup-src-20261017", "_removed")
LIST = os.path.join(HERE, "dead-files.txt")
SAFE_EXT = (".ts", ".tsx", ".js", ".mjs", ".vue")

offset = int(sys.argv[1]) if len(sys.argv) > 1 else 0
limit = int(sys.argv[2]) if len(sys.argv) > 2 else 45

lines = [l.strip() for l in io.open(LIST, encoding="utf-8") if l.strip()]
chunk = lines[offset:offset + limit]

moved = []
skipped = []
for rel in chunk:
    path = os.path.normpath(os.path.join(SRC, rel))
    if not os.path.isfile(path):
        continue
    if not path.endswith(SAFE_EXT):
        skipped.append(rel)
        continue
    if not path.startswith(SRC + os.sep):
        skipped.append(rel)
        continue
    target = os.path.normpath(os.path.join(DEST, rel))
    os.makedirs(os.path.dirname(target), exist_ok=True)
    shutil.move(path, target)
    moved.append(rel)

print("offset=%d limit=%d -> 移出 %d 个（跳过 %d）" % (offset, limit, len(moved), len(skipped)))
for m in moved[:5]:
    print("  - " + m)
if len(moved) > 5:
    print("  ... 共 %d 个" % len(moved))
