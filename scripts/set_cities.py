#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量设置 get_jobs 各平台的「投递城市」（支持多城市）。

用法:
    python scripts/set_cities.py boss 重庆 成都
    python scripts/set_cities.py 51job 重庆 成都
    python scripts/set_cities.py zhilian 重庆 成都

不带城市参数时只打印当前配置，不做修改。
城市名需与各平台 option 表中的名称一致（大小写无关的前后空格会被忽略）。

说明:
    - Boss：多城市由 Boss.execute() 按城市逐个循环投递
    - 51job：多城市拼在同一个搜索 URL 的 jobArea 参数里
    - 智联：多城市由 ZhiLian.execute() 按城市逐个循环投递
"""
import json
import sys
import urllib.request

BASE = "http://localhost:9527"

PLATFORMS = {
    "boss": {
        "endpoint": "/api/boss/config",
        "option_type": "city",
        "field": "cityCode",
        "label": "Boss直聘",
    },
    "51job": {
        "endpoint": "/api/51job/config",
        "option_type": "jobArea",
        "field": "jobArea",
        "label": "51job",
    },
    "zhilian": {
        "endpoint": "/api/zhilian/config",
        "option_type": "city",
        "field": "cityCode",
        "label": "智联招聘",
    },
}


def http(method: str, endpoint: str, payload=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(
        BASE + endpoint, data=data, method=method,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    if len(sys.argv) < 2 or sys.argv[1] not in PLATFORMS:
        print(__doc__)
        return 1

    key = sys.argv[1]
    wanted = [c.strip() for c in sys.argv[2:] if c.strip()]
    meta = PLATFORMS[key]

    cfg = http("GET", meta["endpoint"])
    options = cfg["options"].get(meta["option_type"], [])
    name_to_code = {o["name"]: o["code"] for o in options}

    current = cfg["config"].get(meta["field"])
    print(f"【{meta['label']}】当前城市配置: {current!r}")

    if not wanted:
        print("未指定城市，仅查看。")
        return 0

    codes = []
    for name in wanted:
        code = name_to_code.get(name)
        if code is None:
            print(f"[x] 找不到城市「{name}」，该平台({meta['label']})没有这个城市选项")
            return 1
        codes.append(code)

    entity = dict(cfg["config"])
    entity[meta["field"]] = "[" + ",".join(codes) + "]"

    http("PUT", meta["endpoint"], entity)

    after = http("GET", meta["endpoint"])
    saved = after["config"].get(meta["field"])
    print(f"[OK] 已更新，保存值: {saved!r}")
    for name, code in zip(wanted, codes):
        print(f"      - {name} ({code})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
