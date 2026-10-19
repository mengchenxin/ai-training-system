# -*- coding: utf-8 -*-
"""
从 01-schema.sql 生成 MyBatis-Plus 实体与 Mapper。
只生成骨架，业务方法手写。
"""
import re
import os

BASE = "../src/main/java/com/aitraining/module"

MODULE_OF = {
    "sys_dept": "system", "sys_role": "system", "sys_user": "system",
    "sys_user_role": "system", "post": "system",
    "skill": "skill", "post_skill": "skill", "emp_skill": "skill",
    "train_need": "train", "course": "train", "train_plan": "train",
    "plan_course": "train", "train_progress": "train",
    "question": "exam", "exam_paper": "exam", "exam_paper_question": "exam",
    "exam_record": "exam", "exam_answer": "exam", "answer_sheet": "exam",
    "kb_doc": "ai", "kb_chunk": "ai", "ai_prompt_template": "ai",
    "ai_gen_record": "ai", "ai_qa_log": "ai", "effect_eval": "ai",
}

JAVA_TYPE = {
    "BIGINT": "Long", "INT": "Integer", "TINYINT": "Integer",
    "DECIMAL": "BigDecimal", "VARCHAR": "String", "TEXT": "String",
    "LONGTEXT": "String", "JSON": "String", "DATETIME": "LocalDateTime",
    "DATE": "LocalDate",
}


def camel(name, upper_first=True):
    parts = name.split("_")
    s = "".join(p.capitalize() for p in parts)
    return s if upper_first else s[0].lower() + s[1:]


def parse_ddl(path):
    sql = open(path, encoding="utf-8").read()
    tables = []
    # 抓 CREATE TABLE xxx ( ... ) ENGINE
    for m in re.finditer(
            r"CREATE TABLE (\w+) \((.*?)\n\) ENGINE", sql, re.S):
        tname, body = m.group(1), m.group(2)
        cols = []
        for line in body.split("\n"):
            line = line.strip().rstrip(",")
            if not line or line.startswith(("PRIMARY", "UNIQUE", "KEY", "FULLTEXT", "CONSTRAINT")):
                continue
            cm = re.match(r"(\w+)\s+([A-Z]+)(\([\d,]+\))?(.*)", line, re.S)
            if not cm:
                continue
            col, ctype, _, rest = cm.group(1), cm.group(2), cm.group(3), cm.group(4)
            comment = ""
            cmt = re.search(r"COMMENT\s+'([^']*)'", rest)
            if cmt:
                comment = cmt.group(1)
            # 多行 COMMENT 在下一行的情况
            cols.append({
                "col": col,
                "type": JAVA_TYPE.get(ctype, "String"),
                "comment": comment,
            })
        tables.append({"name": tname, "cols": cols})
    return tables


def main():
    tables = parse_ddl("../sql/01-schema.sql")
    print("解析到表:", len(tables))
    for t in tables:
        tname = t["name"]
        cls = camel(tname)
        mod = MODULE_OF.get(tname, "system")
        pkg = "com.aitraining.module.%s" % mod
        # ---- entity ----
        imports = {"import com.baomidou.mybatisplus.annotation.*;",
                   "import lombok.Data;",
                   "import java.io.Serializable;"}
        types = {c["type"] for c in t["cols"]}
        if "BigDecimal" in types:
            imports.add("import java.math.BigDecimal;")
        if "LocalDateTime" in types:
            imports.add("import java.time.LocalDateTime;")
        if "LocalDate" in types:
            imports.add("import java.time.LocalDate;")

        lines = ["package %s.entity;" % pkg, ""]
        lines += sorted(imports)
        lines += ["", "/**", " * %s" % tname, " */", "@Data",
                  '@TableName("%s")' % tname,
                  "public class %s implements Serializable {" % cls, ""]
        for c in t["cols"]:
            if c["comment"]:
                lines.append("    /** %s */" % c["comment"])
            if c["col"] == "id":
                lines.append("    @TableId(type = IdType.AUTO)")
            lines.append("    private %s %s;" % (c["type"], camel(c["col"], False)))
            lines.append("")
        lines.append("}")
        epath = os.path.join(BASE, mod, "entity", cls + ".java")
        os.makedirs(os.path.dirname(epath), exist_ok=True)
        open(epath, "w", encoding="utf-8").write("\n".join(lines))

        # ---- mapper ----
        ml = ["package %s.mapper;" % pkg, "",
              "import com.baomidou.mybatisplus.core.mapper.BaseMapper;",
              "import %s.entity.%s;" % (pkg, cls),
              "import org.apache.ibatis.annotations.Mapper;", "",
              "@Mapper", "public interface %sMapper extends BaseMapper<%s> {" % (cls, cls), "}"]
        mpath = os.path.join(BASE, mod, "mapper", cls + "Mapper.java")
        os.makedirs(os.path.dirname(mpath), exist_ok=True)
        open(mpath, "w", encoding="utf-8").write("\n".join(ml))
    print("生成完成")


if __name__ == "__main__":
    main()
