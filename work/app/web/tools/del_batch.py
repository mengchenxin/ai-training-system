# -*- coding: utf-8 -*-
"""分批删除 dead-remain.txt 中的死代码文件。

用法: python del_batch.py <offset> <limit>
只删 .ts/.tsx/.js/.mjs/.vue，且路径必须在 src/ 之下。
"""
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(HERE, "..", "src"))
LIST = os.path.join(HERE, "dead-remain.txt")
SAFE_EXT = (".ts", ".tsx", ".js", ".mjs", ".vue")

offset = int(sys.argv[1]) if len(sys.argv) > 1 else 0
limit = int(sys.argv[2]) if len(sys.argv) > 2 else 20

lines = [l.strip() for l in io.open(LIST, encoding="utf-8") if l.strip()]
chunk = lines[offset:offset + limit]

removed = []
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
    os.remove(path)
    removed.append(rel)

print("批次 offset=%d limit=%d -> 删除 %d 个（跳过 %d）" % (offset, limit, len(removed), len(skipped)))
for r in removed:
    print("  - " + r)
