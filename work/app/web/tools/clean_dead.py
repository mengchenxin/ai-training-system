# -*- coding: utf-8 -*-
"""
按 dead-files.txt 删除不可达源码，并清理删空的目录。
只删 .ts/.tsx/.js/.vue，绝不触碰 assets / public / 样式文件。
"""
import io
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(HERE, "..", "src"))
LIST = os.path.join(HERE, "dead-files.txt")

SAFE_EXT = (".ts", ".tsx", ".js", ".mjs", ".vue")

removed = 0
skipped = []

for line in io.open(LIST, encoding="utf-8"):
    rel = line.strip()
    if not rel:
        continue
    path = os.path.normpath(os.path.join(SRC, rel))
    if not os.path.isfile(path):
        continue
    if not path.endswith(SAFE_EXT):
        skipped.append(rel)
        continue
    # 双保险：路径必须在 src 之下
    if not path.startswith(SRC + os.sep):
        skipped.append(rel)
        continue
    os.remove(path)
    removed += 1

# 自底向上删除空目录
emptied = 0
for root, dirs, files in os.walk(SRC, topdown=False):
    if root == SRC:
        continue
    if not os.listdir(root):
        os.rmdir(root)
        emptied += 1

print("已删除源码文件 %d 个" % removed)
print("已清理空目录 %d 个" % emptied)
if skipped:
    print("跳过（非源码扩展名）: %s" % skipped)
