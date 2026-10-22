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
COMMIT_MSG = """feat: 社团朋友圈 MVP —— 图文动态 + 定位打卡 + 评论点赞

- 账号：昵称 + PBKDF2 加盐口令 + emoji 头像，注册完直接进
- 动态：2000 字正文 + 最多 9 张图（九宫格布局）+ 话题 + 定位，点位置跳高德地图
- 互动：评论（可删自己的）、幂等点赞（重复调用不叠加）
- 上传：限制 jpg/png/gif/webp/heic 且 <=10MB，ImageIO 实际解码校验，改后缀的假图会被拒
- 存储：H2 文件库零安装，换 MySQL 只改配置；口令用 PBKDF2 + 每用户独立盐
- 前端：免构建原生 HTML/CSS/JS，用户内容一律转义防 XSS，手机优先布局
- 工程：e2e_test.py（33 项端到端自测）+ seed.py（演示数据）
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
