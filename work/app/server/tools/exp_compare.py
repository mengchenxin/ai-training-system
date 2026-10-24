# -*- coding: utf-8 -*-
"""规则 vs 混合 对比实验：同一批员工各跑两种模式"""
import json, time, urllib.request, urllib.error, sys

BASE = 'http://127.0.0.1:8000/api'
# 关键：清空代理，避免本地请求被系统代理劫持
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(path, body=None, token=None, timeout=180):
    data = json.dumps(body).encode('utf-8') if body is not None else b''
    req = urllib.request.Request(BASE + path, data=data, method='POST')
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    with OPENER.open(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode('utf-8'))


def login():
    r = call('/auth/login', {'username': 'admin', 'password': '123456'})
    return r['data']['token']


def main():
    token = login()
    print('登录成功')

    users = list(range(5, 25))  # 20 个员工
    results = []
    total = len(users) * 2
    done = 0

    for uid in users:
        for use_llm in (True, False):
            done += 1
            mode = 'AI_HYBRID' if use_llm else 'RULE'
            item = {'userId': uid, 'useLlm': use_llm, 'mode': mode}
            t0 = time.time()
            try:
                r = call('/ai/plan/generate?userId=%d&useLlm=%s' % (uid, 'true' if use_llm else 'false'),
                         token=token)
                if r.get('code') != 0:
                    item['status'] = 'BIZ_ERROR'
                    item['error'] = r.get('msg')
                else:
                    d = r.get('data') or {}
                    item['status'] = 'OK'
                    item['genType'] = d.get('genType')
                    item['llmUsed'] = d.get('llmUsed')
                    item['schemaValid'] = d.get('schemaValid')
                    item['costMs'] = d.get('costMs')
                    item['fallbackReason'] = d.get('fallbackReason')
                    item['courseIds'] = sorted([c.get('courseId') for c in (d.get('courses') or [])])
                    item['courseCount'] = len(item['courseIds'])
                    item['refCount'] = len(d.get('refs') or [])
                    item['totalHours'] = d.get('totalHours')
                    item['summaryLen'] = len(d.get('summary') or '')
                    item['recordId'] = d.get('aiRecordId')
            except urllib.error.HTTPError as e:
                item['status'] = 'HTTP_%d' % e.code
                item['error'] = e.read().decode('utf-8', 'replace')[:150]
            except Exception as e:
                item['status'] = 'EXC'
                item['error'] = str(e)[:150]
            item['wallMs'] = int((time.time() - t0) * 1000)
            results.append(item)
            print('[%2d/%2d] user=%2d %-9s -> %-10s %s' % (
                done, total, uid, mode, item['status'],
                ('%d课/%.0fms' % (item.get('courseCount', 0), item.get('costMs') or 0)) if item['status'] == 'OK' else item.get('error', '')))

    with open(r'C:\Users\Public\exp_result.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=1)

    # ---- 汇总 ----
    def stats(mode):
        xs = [r for r in results if r['mode'] == mode]
        ok = [r for r in xs if r['status'] == 'OK']
        biz = [r for r in xs if r['status'] == 'BIZ_ERROR']
        n = len(xs)
        costs = sorted([r['costMs'] for r in ok if r.get('costMs')])
        med = costs[len(costs) // 2] if costs else 0
        return {
            'n': n, 'ok': len(ok), 'biz': len(biz),
            'schema_pass': sum(1 for r in ok if r.get('schemaValid')),
            'llm_used': sum(1 for r in ok if r.get('llmUsed')),
            'avg_cost': round(sum(costs) / len(costs), 1) if costs else 0,
            'median_cost': med,
            'avg_course': round(sum(r['courseCount'] for r in ok) / len(ok), 2) if ok else 0,
            'avg_ref': round(sum(r['refCount'] for r in ok) / len(ok), 2) if ok else 0,
            'avg_hours': round(sum(float(r.get('totalHours') or 0) for r in ok) / len(ok), 2) if ok else 0,
        }

    s1, s2 = stats('AI_HYBRID'), stats('RULE')
    print('\n================ 对比汇总 ================')
    print('%-22s %-14s %-14s' % ('指标', '规则(RULE)', '混合(HYBRID)'))
    print('-' * 54)
    for k, label in [('n', '样本数'), ('ok', '成功'), ('biz', '业务拒绝(无差距)'),
                     ('schema_pass', 'Schema 校验通过'), ('llm_used', '实际调用模型'),
                     ('avg_cost', '平均耗时(ms)'), ('median_cost', '耗时中位数(ms)'),
                     ('avg_course', '平均课程数'), ('avg_hours', '平均总学时'),
                     ('avg_ref', '平均引用数')]:
        print('%-20s %-14s %-14s' % (label, s2.get(k), s1.get(k)))

    # 课程重合度
    overlap = []
    for uid in users:
        a = next((r for r in results if r['userId'] == uid and r['mode'] == 'AI_HYBRID' and r['status'] == 'OK'), None)
        b = next((r for r in results if r['userId'] == uid and r['mode'] == 'RULE' and r['status'] == 'OK'), None)
        if a and b and (a['courseIds'] or b['courseIds']):
            sa, sb = set(a['courseIds']), set(b['courseIds'])
            union = sa | sb
            overlap.append(len(sa & sb) / len(union) if union else 1.0)
    if overlap:
        print('\n课程集合重合度(Jaccard) 平均: %.3f  最小: %.3f  最大: %.3f  n=%d' % (
            sum(overlap) / len(overlap), min(overlap), max(overlap), len(overlap)))


if __name__ == '__main__':
    main()
