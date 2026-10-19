# -*- coding: utf-8 -*-
"""
从 src/main.ts 出发做模块可达性分析，找出模板遗留的不可达文件。
支持 @/ 别名、相对路径、省略扩展名、index.ts / index.vue。
"""
import io
import os
import re
import sys

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src")
SRC = os.path.normpath(SRC)

IMPORT_RE = re.compile(
    r"""(?:from\s+["']([^"']+)["']|import\s*\(\s*["']([^"']+)["']\s*\))""", re.S)

EXTS = [".ts", ".tsx", ".vue", ".js", ".mjs", ".json"]


def resolve(spec, cur_file):
    if spec.startswith("."):
        base = os.path.normpath(os.path.join(os.path.dirname(cur_file), spec))
    elif spec.startswith("@/"):
        base = os.path.normpath(os.path.join(SRC, spec[2:]))
    else:
        return None  # 第三方包
    # 精确文件
    for e in EXTS:
        if os.path.isfile(base + e):
            return base + e
    if os.path.isfile(base):
        return base
    # 目录 index
    for e in EXTS:
        p = os.path.join(base, "index" + e)
        if os.path.isfile(p):
            return p
    # 带扩展名但指向目录
    for e in EXTS:
        p = os.path.join(base, "index" + e)
        if os.path.isfile(p):
            return p
    return None


def deps_of(path):
    try:
        text = io.open(path, encoding="utf-8", errors="ignore").read()
    except Exception:
        return []
    out = []
    for m in IMPORT_RE.finditer(text):
        spec = m.group(1) or m.group(2)
        if not spec:
            continue
        r = resolve(spec, path)
        if r:
            out.append(os.path.normpath(r))
    return out


def main():
    entry = os.path.join(SRC, "main.ts")
    seen = set()
    stack = [os.path.normpath(entry)]
    while stack:
        cur = stack.pop()
        if cur in seen:
            continue
        seen.add(cur)
        for d in deps_of(cur):
            if d not in seen:
                stack.append(d)

    all_files = []
    for root, dirs, files in os.walk(SRC):
        for f in files:
            if f.endswith((".ts", ".tsx", ".vue", ".js")):
                all_files.append(os.path.normpath(os.path.join(root, f)))

    reach = sorted(x for x in all_files if x in seen)
    dead = sorted(x for x in all_files if x not in seen)

    def rel(p):
        return os.path.relpath(p, SRC).replace("\\", "/")

    print("=" * 70)
    print("可达文件 %d 个，不可达文件 %d 个" % (len(reach), len(dead)))
    print("=" * 70)
    print("\n【不可达 —— 模板遗留死代码】")
    by_dir = {}
    for d in dead:
        r = rel(d)
        top = r.split("/")[0] if "/" in r else "(根)"
        sub = "/".join(r.split("/")[:2]) if r.count("/") >= 1 else r
        key = sub if r.count("/") >= 1 else r
        by_dir.setdefault(key, []).append(r)
    for k in sorted(by_dir):
        print("  %-42s %d 个" % (k, len(by_dir[k])))

    print("\n【可达 —— 当前实际使用的文件】")
    for r in reach:
        print("  " + rel(r))

    with io.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "dead-files.txt"),
                 "w", encoding="utf-8") as f:
        for d in dead:
            f.write(rel(d) + "\n")
    print("\n死文件清单已写入 tools/dead-files.txt")


if __name__ == "__main__":
    main()
