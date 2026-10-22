"""把本地已跟踪的文件通过 GitHub Git Data API 推上去。

为什么不直接 git push：本机 credential store 不可写、弹凭据也不方便时，
走 API 是等价且更可控的路径（token 全程只从文件读出，不经过命令行）。

用法： python push_repo.py
"""

import base64
import json
import os
import ssl
import subprocess
import sys
import urllib.error
import urllib.request

REPO = "xxxin-23/aitecc-moments"
BRANCH = "main"
COMMIT_MSG = """feat: 科技社陪着你走过青葱校园 —— Hello Kitty 主题 + 收藏/推荐/改资料

品牌
- 改名「科技社陪着你走过青葱校园」，换 Hello Kitty 粉嫩主题：粉渐变顶栏、飘浮蝴蝶结爱心、
  圆润卡片 + 粉边框、胶囊互动按钮
- 信息流三档切换：🌟 推荐 / 🕊️ 最新 / ⭐ 我的收藏

互动（点赞评论收藏推荐）
- 新增收藏与推荐：共用 reactions 表靠 kind 区分，联合唯一索引保证幂等
- 推荐流按热度排序：点赞x1 + 评论x2 + 收藏x2 + 推荐x3，再按时间衰减
- 删动态会先清掉点赞/评论/收藏/推荐，解决「被点赞过的动态删不掉」
- 上传放宽 iPhone 的 heic：Java 解码不了但确实是照片，直接放行不被拦

个人资料
- 昵称 / emoji 头像 / 一句话简介都能改（PATCH /api/auth/profile），昵称查重排除自己
- 改完昵称，历史动态的署名同步更新

工程
- 移除 seed.py 演示数据脚本，新增 clean_demo.py 可一键清掉遗留演示数据
- e2e 44 项全过；新功能在独立临时实例上另行验证 21 项（不污染正式库）
"""

CA = os.path.expanduser("~/.git-ca-bundle.crt")
CTX = ssl.create_default_context(cafile=CA) if os.path.exists(CA) else ssl.create_default_context()
OPENER = urllib.request.build_opener(
    urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=CTX)
)


def request(method, path, payload=None):
    """带重试的请求 —— 本机网络偶发 SSL 握手超时，单次失败不代表真的失败。"""
    import time

    token = open(r"C:\Users\x\.github_pat.txt", encoding="utf-8").read().strip()
    last = None
    for attempt in range(5):
        data = json.dumps(payload).encode() if payload is not None else None
        req = urllib.request.Request(f"https://api.github.com{path}", data=data, method=method)
        req.add_header("Authorization", "Bearer " + token)
        req.add_header("User-Agent", "workbuddy")
        req.add_header("Accept", "application/vnd.github+json")
        try:
            r = OPENER.open(req, timeout=45)
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8", "ignore")[:400]
        except Exception as e:  # noqa: BLE001 - SSL 握手超时等网络抖动
            last = e
            print(f"      .. 重试 {attempt + 1}/5（{type(e).__name__}）")
            time.sleep(3 * (attempt + 1))
    return 0, f"网络失败: {last}"


def main():
    # 只推 git 已跟踪的文件，自动尊重 .gitignore
    listing = subprocess.run(
        ["git", "ls-files", "-z"], cwd=os.path.dirname(os.path.abspath(__file__)),
        capture_output=True, check=True,
    ).stdout
    paths = [p.decode("utf-8") for p in listing.split(b"\0") if p]
    if not paths:
        sys.exit("git ls-files 为空，先 git add 再跑")

    print(f"待推送 {len(paths)} 个文件")

    # 1) 逐个建 blob
    tree = []
    for rel in paths:
        with open(rel, "rb") as f:
            content = f.read()
        s, d = request("POST", f"/repos/{REPO}/git/blobs",
                       {"content": base64.b64encode(content).decode(), "encoding": "base64"})
        if s >= 300:
            sys.exit(f"blob 失败 {rel}: {s} {d}")
        tree.append({"path": rel.replace("\\", "/"), "mode": "100644", "type": "blob", "sha": d["sha"]})
    print(f"✅ 已创建 {len(tree)} 个 blob")

    local_set = {t["path"] for t in tree}

    # 2) 建 tree
    #    空仓库没有基础 tree；非空仓库要把远端已有、本地没有的文件（如 LICENSE）带上，
    #    否则新 tree 会把它们悄悄删掉。
    s, ref = request("GET", f"/repos/{REPO}/git/ref/heads/{BRANCH}")
    ref_exists = s == 200
    parents = []
    if ref_exists:
        s, head = request("GET", f"/repos/{REPO}/git/commits/{ref['object']['sha']}")
        if s != 200:
            sys.exit(f"读取 HEAD commit 失败: {s} {head}")
        parents = [head["sha"]]
        s, remote_tree = request(
            "GET", f"/repos/{REPO}/git/trees/{head['tree']['sha']}?recursive=1"
        )
        if s == 200:
            for item in remote_tree.get("tree", []):
                # 只保留顶层文件（blob 已在远端，直接复用 sha）
                if item["type"] == "blob" and item["path"] not in local_set \
                        and "/" not in item["path"] and item["path"] not in (".gitignore", "README.md"):
                    tree.append(
                        {"path": item["path"], "mode": item["mode"], "type": "blob", "sha": item["sha"]}
                    )
                    print(f"  ↩ 保留远端文件：{item['path']}")

    s, t = request("POST", f"/repos/{REPO}/git/trees", {"tree": tree})
    if s >= 300:
        sys.exit(f"tree 失败: {s} {t}")
    print(f"✅ tree {t['sha'][:8]}")

    # 3) 建 commit
    payload = {"message": COMMIT_MSG, "tree": t["sha"]}
    if parents:
        payload["parents"] = parents
    s, c = request("POST", f"/repos/{REPO}/git/commits", payload)
    if s >= 300:
        sys.exit(f"commit 失败: {s} {c}")
    print(f"✅ commit {c['sha'][:8]}")

    # 4) 更新 / 创建分支引用
    if ref_exists:
        s, r = request("PATCH", f"/repos/{REPO}/git/refs/heads/{BRANCH}", {"sha": c["sha"]})
    else:
        s, r = request("POST", f"/repos/{REPO}/git/refs",
                       {"ref": f"refs/heads/{BRANCH}", "sha": c["sha"]})
    if s >= 300:
        sys.exit(f"更新引用失败: {s} {r}")
    print(f"✅ 已更新 refs/heads/{BRANCH}")
    print(f"\n🎉 推送完成：https://github.com/{REPO}/tree/{BRANCH}")


if __name__ == "__main__":
    main()
