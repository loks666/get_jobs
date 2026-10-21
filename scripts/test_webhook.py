#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
企业微信「消息推送」(原群机器人) Webhook 连通性测试脚本。

用法:
    python scripts/test_webhook.py
        -> 交互式粘贴 Webhook 地址

    python scripts/test_webhook.py "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
        -> 直接传入地址

判定:
    errcode == 0  ->  配置正确，群里会收到一条测试消息
    93000         ->  key 无效 / 机器人已被移除
    45009         ->  接口调用超过频率限制(20条/分钟)
"""
import json
import sys
import urllib.error
import urllib.request

ERR_HINT = {
    0: "成功",
    93000: "Webhook key 无效，或该机器人已被从群里移除",
    45009: "接口调用超过频率限制（每个机器人 20 条/分钟）",
    40058: "请求参数不合法，检查是否漏了 content 字段",
}


def main() -> int:
    url = sys.argv[1] if len(sys.argv) > 1 else input("请粘贴 Webhook 地址: ").strip().strip('"')

    if "qyapi.weixin.qq.com" not in url or "key=" not in url:
        print("=" * 60)
        print("[x] 这不是有效的 Webhook 地址。")
        print("    正确格式: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=<32位UUID>")
        print("    你传入的: " + url[:80])
        print("=" * 60)
        return 1

    payload = json.dumps({
        "msgtype": "text",
        "text": {"content": "get_jobs 推送测试\n如果你在群里看到这条消息，说明 Webhook 配置成功。"}
    }).encode("utf-8")

    req = urllib.request.Request(
        url, data=payload, method="POST",
        headers={"Content-Type": "application/json"},
    )

    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        print(f"[x] HTTP {e.code}: {e.read().decode('utf-8', 'ignore')}")
        return 1
    except Exception as e:  # noqa: BLE001
        print(f"[x] 请求失败: {e}")
        return 1

    try:
        result = json.loads(body)
    except json.JSONDecodeError:
        print(f"[x] 响应不是 JSON: {body}")
        return 1

    errcode = result.get("errcode")
    print("=" * 60)
    print(f"errcode = {errcode}   errmsg = {result.get('errmsg')}")
    print(ERR_HINT.get(errcode, ""))
    if errcode == 0:
        print("\n[OK] 配置成功！去企业微信群里看一下，应该收到测试消息了。")
        print("     接下来把下面这行填进 http://localhost:6866 的环境配置页 -> HOOK_URL")
        print(f"     {url}")
    print("=" * 60)
    return 0 if errcode == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
