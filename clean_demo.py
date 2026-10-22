"""清掉 seed.py 灌进去的演示动态和它们的图片，让平台交到同学手上时是干净的。

演示账号的密码是 seed.py 里写死的 demo123456，删帖必须由作者本人操作，
所以这里逐个登录演示账号再删自己发的动态。
用法：python clean_demo.py [base_url]
"""

import json
import os
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"
DEMO_USERS = ["小螺蛳", "图书馆常驻", "不加冰", "湖边的风"]
DEMO_PASSWORD = "demo123456"
UPLOAD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads")

urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))


def http(method, path, body=None, headers=None):
    req = urllib.request.Request(BASE + path, data=body, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        r = urllib.request.urlopen(req, timeout=40)
        raw = r.read()
        return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")
    except Exception as e:  # 连不上服务
        return 0, str(e)


def main():
    status, data = http("GET", "/api/posts?sort=latest&size=100")
    if status != 200:
        print(f"拉取动态失败（{status}）：{data}")
        return 1
    posts = data["items"]
    print(f"当前共 {len(posts)} 条动态")

    removed_posts = 0
    removed_files = 0

    for nickname in DEMO_USERS:
        s, r = http(
            "POST",
            "/api/auth/login",
            json.dumps({"nickname": nickname, "password": DEMO_PASSWORD}).encode(),
            {"Content-Type": "application/json"},
        )
        if s != 200:
            print(f"- {nickname}：登录失败（{s}），跳过")
            continue
        token = r["token"]
        mine = [p for p in posts if p["author"]["nickname"] == nickname]
        for p in mine:
            urls = [im["url"] for im in p.get("images") or []]
            s2, _ = http("DELETE", "/api/posts/" + str(p["id"]), None, {"X-Auth-Token": token})
            if s2 in (200, 204):
                removed_posts += 1
                for u in urls:
                    name = u.rsplit("/", 1)[-1]
                    fp = os.path.join(UPLOAD_DIR, name)
                    if os.path.exists(fp):
                        os.remove(fp)
                        removed_files += 1
                print(f"- 已删除 {nickname} 的动态 #{p['id']}（{len(urls)} 张图）")
            else:
                print(f"- 删除失败 #{p['id']}（{s2}）")

    s, data = http("GET", "/api/posts?sort=latest&size=100")
    left = len(data["items"]) if s == 200 else -1
    print(f"\n完成：删掉 {removed_posts} 条演示动态、{removed_files} 张演示图片，还剩 {left} 条")
    return 0


if __name__ == "__main__":
    sys.exit(main())
