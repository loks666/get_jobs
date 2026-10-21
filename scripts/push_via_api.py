#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通过 GitHub Git Data API 推送本地提交（一次性的临时工具）。

用途：当 git-over-HTTPS 不可用（例如 pack 端点被掐断）但 REST API 可用时，
用它把本地若干个提交原样推送到远端分支。

用法:
    set GH_TOKEN=<你的 PAT>
    python push_via_api.py

特性:
    - 逐个提交，保持本地的提交历史粒度，而不是合并成一个大 commit
    - 每个 commit 只携带它自己改动的文件（tree 基于父提交的 tree 增量构建）
    - 行尾统一转为 LF，避免 core.autocrlf=true 带来的 CRLF 污染
"""
import io
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
API = "https://api.github.com"
OWNER, REPO = "Plane-T", "get_jobs"
BASE_BRANCH = "main"
TARGET_BRANCH = "feat/multi-city"

TOKEN = os.environ.get("GH_TOKEN")
if not TOKEN:
    sys.exit("请设置 GH_TOKEN 环境变量")

def resolve_shas():
    """
    决定要推送哪些提交。

    - PUSH_MODE=rebuild（默认）：忽略远端分支现状，从 main 重新依次构建整条链，
      得到与本地一一对应的干净历史。适用于远端历史被污染时重置。
    - PUSH_MODE=incremental：只推送远端分支上还没有的提交。
    """
    all_local = [s.strip() for s in
                 git("rev-list", "--reverse", "{}..HEAD".format(BASE_BRANCH)).split() if s.strip()]

    if os.environ.get("PUSH_MODE", "rebuild").lower() != "incremental":
        return all_local, True

    try:
        remote_commits = api("GET", "/repos/{}/{}/commits?sha={}&per_page=100"
                             .format(OWNER, REPO, TARGET_BRANCH))
        remote_titles = {c["commit"]["message"].strip() for c in remote_commits}
    except SystemExit:
        return all_local, ref_sha(TARGET_BRANCH) is None

    if ref_sha(TARGET_BRANCH) is None:
        return all_local, True

    pending = [sha for sha in all_local
               if git("log", "--format=%s", "-1", sha).strip() not in remote_titles]
    return pending, False


def resolve_pending_shas():
    """
    计算「本地有、但远端目标分支还没有」的提交。

    通过 API 创建的 commit 与本地 commit 内容一致但 sha 不同，所以不能用 sha 比较；
    这里改用提交标题匹配，找出第一个已在远端的提交，其后所有提交即为待推送。
    """
    try:
        remote_commits = api("GET", "/repos/{}/{}/commits?sha={}&per_page=100"
                             .format(OWNER, REPO, TARGET_BRANCH))
        remote_titles = {c["commit"]["message"].strip() for c in remote_commits}
    except SystemExit:
        return None

    # 远端分支不存在 -> 全部都要推
    if ref_sha(TARGET_BRANCH) is None:
        return [s.strip() for s in
                git("rev-list", "--reverse", "{}..HEAD".format(BASE_BRANCH)).split() if s.strip()]

    # 否则只推本地还没出现在远端分支上的提交（按提交标题比对）
    pending = []
    for sha in git("rev-list", "--reverse", "{}..HEAD".format(BASE_BRANCH)).split():
        sha = sha.strip()
        if not sha:
            continue
        title = git("log", "--format=%s", "-1", sha).strip()
        if title not in remote_titles:
            pending.append(sha)
    return pending


def api(method, path, payload=None):
    url = API + path
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": "Bearer " + TOKEN,
        "Accept": "application/vnd.github+json",
        "User-Agent": "push-via-api",
        "Content-Type": "application/json",
    })
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            body = resp.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        sys.exit("HTTP {} {}\n{}".format(e.code, path, e.read().decode("utf-8", "ignore")[:800]))


def git(*args):
    out = subprocess.run(["git"] + list(args), cwd=ROOT, capture_output=True)
    return out.stdout.decode("utf-8", "replace")


def read_file(path):
    full = os.path.join(ROOT, path)
    with io.open(full, encoding="utf-8", newline="") as f:
        return f.read().replace("\r\n", "\n")


def ref_sha(branch):
    try:
        return api("GET", "/repos/{}/{}/git/ref/heads/{}".format(OWNER, REPO, branch))["object"]["sha"]
    except SystemExit:
        return None


def files_of(sha):
    return [l.strip() for l in git("show", "--name-only", "--pretty=format:", sha).splitlines() if l.strip()]


def message_of(sha):
    return git("log", "--format=%B", "-1", sha).strip()


def main():
    shas, is_new = resolve_shas()

    if is_new or os.environ.get("PUSH_MODE", "rebuild").lower() == "rebuild":
        parent = ref_sha(BASE_BRANCH)
        print("重建：基线 {} = {}".format(BASE_BRANCH, parent[:10]))
    else:
        parent = ref_sha(TARGET_BRANCH)
        print("增量：基线 head = {}".format(parent[:10]))

    if not shas:
        print("\n没有需要推送的新提交")
        return

    print("待推送提交: {}".format(", ".join(shas)))

    for short in shas:
        files = files_of(short)
        message = message_of(short)
        print("\n处理提交 {} ({} 个文件)".format(short, len(files)))

        base_tree = api("GET", "/repos/{}/{}/git/commits/{}".format(OWNER, REPO, parent))["tree"]["sha"]

        tree_items = [{
            "path": p,
            "mode": "100644",
            "type": "blob",
            "content": read_file(p),
        } for p in files]

        tree = api("POST", "/repos/{}/{}/git/trees".format(OWNER, REPO), {
            "base_tree": base_tree,
            "tree": tree_items,
        })

        commit = api("POST", "/repos/{}/{}/git/commits".format(OWNER, REPO), {
            "message": message,
            "tree": tree["sha"],
            "parents": [parent],
        })

        print("  -> {}".format(commit["sha"][:10]))
        parent = commit["sha"]

    if ref_sha(TARGET_BRANCH) is None:
        api("POST", "/repos/{}/{}/git/refs".format(OWNER, REPO), {
            "ref": "refs/heads/" + TARGET_BRANCH,
            "sha": parent,
        })
        print("\n分支已创建")
    else:
        # 新 commit 的 parent 就是远端 head，因此一定是快进
        api("PATCH", "/repos/{}/{}/git/refs/heads/{}".format(OWNER, REPO, TARGET_BRANCH),
            {"sha": parent, "force": True})
        print("\n分支已更新")

    print("\n完成: {}/{}/tree/{}".format(OWNER, REPO, TARGET_BRANCH))


if __name__ == "__main__":
    main()
