# -*- coding: utf-8 -*-
"""把 lecturer01/emp001 的姓名改为张三/李四，其余字段原样保留。

通过系统自身接口 POST /api/system/user 提交，不直连数据库。
"""
import json

from rename_users import call, find_user, login

TARGETS = {"lecturer01": "张三", "emp001": "李四"}


def build_form(row, real_name):
    """用查询结果回填完整表单，避免把部门/岗位/角色覆盖为空。"""
    return {
        "id": row["id"],
        "username": row["username"],
        "realName": real_name,
        "deptId": row.get("deptId"),
        "postId": row.get("postId"),
        "phone": row.get("phone"),
        "email": row.get("email"),
        "status": row.get("status", 1),
        "roleIds": row.get("roleIds") or [],
    }


def main():
    tk = login()
    for username, new_name in TARGETS.items():
        row = find_user(tk, username)
        if not row:
            print("[FAIL]", username, "未找到")
            continue
        old = row.get("realName")
        form = build_form(row, new_name)
        d = call("POST", "/system/user", tk, form)
        print("%-11s id=%-3s %s -> %s | code=%s %s"
              % (username, row["id"], old, new_name, d.get("code"), d.get("msg")))

    print("\n=== 回读校验 ===")
    for username, expect in TARGETS.items():
        row = find_user(tk, username)
        ok = row and row.get("realName") == expect
        print("%-11s realName=%-4s dept=%s post=%s roles=%s -> %s"
              % (username, row.get("realName"), row.get("deptName"),
                 row.get("postName"), row.get("roles"), "OK" if ok else "MISMATCH"))

    print("\n=== 用改后账号登录校验 ===")
    for username, expect in TARGETS.items():
        d = call("POST", "/auth/login", body={"username": username, "password": "123456"})
        data = d.get("data") or {}
        print("%-11s 登录 code=%s | realName=%s | roles=%s"
              % (username, d.get("code"), data.get("realName"), data.get("roles")))


if __name__ == "__main__":
    main()
