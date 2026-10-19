# -*- coding: utf-8 -*-
"""查询/修改演示账号姓名，全程走系统 HTTP 接口，不直连数据库。"""
import json
import sys
import urllib.request

BASE = "http://127.0.0.1:8000/api"

# 本机访问不要走环境代理（127.0.0.1 也会被 HTTP_PROXY 接管）
_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with _OPENER.open(req, timeout=15) as r:
        return json.loads(r.read().decode("utf-8"))


def login(username="admin", pwd="123456"):
    d = call("POST", "/auth/login", body={"username": username, "password": pwd})
    assert d.get("code") == 0, d
    return d["data"]["token"]


def find_user(token, username):
    d = call("GET", "/system/user/page?current=1&size=500", token)
    assert d.get("code") == 0, d
    rows = (d.get("data") or {}).get("list") or (d.get("data") or {}).get("records") or []
    for r in rows:
        if r.get("username") == username:
            return r
    return None


if __name__ == "__main__":
    tk = login()
    for u in ("lecturer01", "emp001"):
        r = find_user(tk, u)
        if not r:
            print(u, "未找到")
            continue
        print(u, "->", json.dumps(r, ensure_ascii=False))
