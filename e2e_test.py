"""aitecc-moments 端到端接口自测：注册 → 登录 → 发图 → 评论 → 点赞 → 删除。

运行前确保服务已起：mvn spring-boot:run
用法： python e2e_test.py [base_url]
"""

import base64
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"

def make_png(width, height, rgb):
    """现场生成一张合法的 PNG —— 顺便验证服务端的 ImageIO 解码校验确实放得行真图。"""
    import struct
    import zlib

    raw = b"".join(b"\x00" + bytes(rgb) * width for _ in range(height))

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)  # 8bit 真彩色
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


# 三张不同颜色的测试图
IMAGES = [
    ("fen.png", make_png(8, 8, (255, 120, 120))),
    ("tang.png", make_png(8, 8, (120, 200, 255))),
    ("cai.png", make_png(8, 8, (255, 220, 100))),
]

passed, failed = 0, 0


def check(name, cond, extra=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  ✅ {name}")
    else:
        failed += 1
        print(f"  ❌ {name} {extra}")


def http(method, path, body=None, headers=None):
    # 中文话题名要百分号编码后才能进 URL
    safe = "".join(
        urllib.parse.quote(ch, safe="/?=&=%") if ord(ch) > 127 else ch for ch in path
    )
    req = urllib.request.Request(BASE + safe, data=body, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        r = urllib.request.urlopen(req, timeout=40)
        raw = r.read()
        ct = r.headers.get("Content-Type", "")
        return r.status, (json.loads(raw) if "application/json" in ct else raw), r.headers
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw), e.headers
        except Exception:
            return e.code, raw, e.headers


def multipart(fields, files):
    boundary = "----wb" + uuid.uuid4().hex
    out = b""
    for k, v in fields.items():
        out += (
            f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n'.encode()
            + str(v).encode("utf-8")
            + b"\r\n"
        )
    for field, filename, content, ctype in files:
        out += (
            f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; '
            f'filename="{filename}"\r\nContent-Type: {ctype}\r\n\r\n'.encode()
        )
        out += content + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return boundary, out


def json_headers(token=None):
    h = {"Content-Type": "application/json"}
    if token:
        h["X-Auth-Token"] = token
    return h


def main():
    # 本机直连，别被环境里的出口代理劫走
    urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
    stamp = uuid.uuid4().hex[:6]
    nick_a = f"唐欣{stamp}"
    nick_b = f"小明{stamp}"

    print("=== 0. 健康检查 ===")
    s, d, _ = http("GET", "/api/ping")
    check("GET /api/ping 返回 200", s == 200, f"got {s}")
    check("首页静态资源可访问", (lambda r: r[0] == 200)(http("GET", "/")))

    print("\n=== 1. 注册与登录 ===")
    s, a, _ = http(
        "POST",
        "/api/auth/register",
        json.dumps({"nickname": nick_a, "password": "test123456", "avatar": "🍜"}).encode(),
        json_headers(),
    )
    check("注册用户 A", s == 200 and a.get("token"), f"got {s} {a if s != 200 else ''}")
    token_a = a.get("token", "")

    s, b, _ = http(
        "POST",
        "/api/auth/register",
        json.dumps({"nickname": nick_b, "password": "test123456", "avatar": "🎮"}).encode(),
        json_headers(),
    )
    check("注册用户 B", s == 200 and b.get("token"), f"got {s}")
    token_b = b.get("token", "")

    s, d, _ = http(
        "POST",
        "/api/auth/register",
        json.dumps({"nickname": nick_a, "password": "test123456"}).encode(),
        json_headers(),
    )
    check("重名注册被拒", s == 400 and "昵称" in str(d), f"got {s} {d}")

    s, lg, _ = http(
        "POST",
        "/api/auth/login",
        json.dumps({"nickname": nick_a, "password": "test123456"}).encode(),
        json_headers(),
    )
    check("登录成功并返回新令牌", s == 200 and lg.get("token"), f"got {s}")
    token_a = lg.get("token", token_a)

    s, d, _ = http(
        "POST",
        "/api/auth/login",
        json.dumps({"nickname": nick_a, "password": "wrongpwd"}).encode(),
        json_headers(),
    )
    check("错误密码被拒", s == 401, f"got {s}")

    s, me, _ = http("GET", "/api/auth/me", headers={"X-Auth-Token": token_a})
    check("GET /api/auth/me 拿到本人", s == 200 and me.get("nickname") == nick_a, f"got {s}")

    print("\n=== 2. 发布带图 + 定位动态 ===")
    boundary, body = multipart(
        {
            "content": "东校区后街新开的螺蛳粉，汤头很足 🍜 #好吃到跺脚",
            "topic": "美食",
            "locationName": "广西民族大学东校区后街",
            "latitude": 22.7936,
            "longitude": 108.3651,
        },
        [
            ("images", name, data, "image/png") for name, data in IMAGES
        ],
    )
    s, post, _ = http(
        "POST",
        "/api/posts",
        body,
        {"Content-Type": f"multipart/form-data; boundary={boundary}", "X-Auth-Token": token_a},
    )
    check("发布动态成功", s == 200 and post.get("id"), f"got {s} {post if s != 200 else ''}")
    post_id = post.get("id")
    if not post_id:
        print("\n❌ 发布失败，后续测试无法继续，先修上面的问题")
        sys.exit(1)
    check("三张图都入库", len(post.get("images", [])) == 3, f"got {len(post.get('images', []))}")
    check("定位信息保留", (post.get("location") or {}).get("name") == "广西民族大学东校区后街")
    check("话题标签保留", post.get("topic") == "美食")

    # 图片能否真的取回来
    if post.get("images"):
        url = post["images"][0]["url"]
        s, raw, h = http("GET", url)
        is_img = s == 200 and isinstance(raw, (bytes, bytearray)) and raw[:8] == b"\x89PNG\r\n\x1a\n"
        check(f"上传的图片可访问 ({url})", is_img, f"got {s}")

    print("\n=== 2.5 编辑自己发的动态 ===")
    s, edited, _ = http(
        "PATCH",
        f"/api/posts/{post_id}",
        json.dumps(
            {
                "content": "改一下：这家其实更好吃",
                "topic": "探店",
                "locationName": "西校区食堂三楼",
                "latitude": 22.84,
                "longitude": 108.33,
            }
        ).encode(),
        json_headers(token_a),
    )
    check("作者可以编辑自己的动态", s == 200 and edited.get("content") == "改一下：这家其实更好吃",
          f"got {s} {edited if s != 200 else ''}")
    check("编辑后话题跟着变", edited.get("topic") == "探店", f"got {edited.get('topic')}")
    check("编辑后定位跟着变", (edited.get("location") or {}).get("name") == "西校区食堂三楼")
    check("编辑不动已有图片", len(edited.get("images", [])) == 3,
          f"got {len(edited.get('images', []))}")

    s, after, _ = http("GET", f"/api/posts/{post_id}")
    check("编辑结果已落库", after.get("content") == "改一下：这家其实更好吃", f"got {after.get('content')}")

    s, _, _ = http("PATCH", f"/api/posts/{post_id}",
                   json.dumps({"content": "别人来改"}).encode(), json_headers(token_b))
    check("改别人的动态被拒 403", s == 403, f"got {s}")

    s, _, _ = http("PATCH", f"/api/posts/{post_id}",
                   json.dumps({"content": "x" * 2001}).encode(), json_headers(token_a))
    check("正文超过 2000 字被拒 400", s == 400, f"got {s}")

    s, _, _ = http("PATCH", "/api/posts/999999",
                   json.dumps({"content": "不存在"}).encode(), json_headers(token_a))
    check("编辑不存在的动态 404", s == 404, f"got {s}")

    s, _, _ = http(
        "PATCH", f"/api/posts/{post_id}",
        json.dumps({"content": "没登录也想改"}).encode(),
        {"Content-Type": "application/json"},
    )
    check("没有登录令牌编辑被拒 401", s == 401, f"got {s}")

    s, _, _ = http("PATCH", f"/api/posts/{post_id}", b"", json_headers(token_a))
    check("空请求体编辑被拒 400（不允许误清空）", s == 400, f"got {s}")

    # B 发一条纯文字动态，验证「清空正文且无图」时会被拒（A 那条有图，清空是合法的）
    boundary2, body2 = multipart({"content": "临时占位"}, [])
    s, plain, _ = http(
        "POST", "/api/posts", body2,
        {"Content-Type": f"multipart/form-data; boundary={boundary2}", "X-Auth-Token": token_b},
    )
    if s == 200 and plain.get("id"):
        s, _, _ = http("PATCH", f"/api/posts/{plain['id']}",
                       json.dumps({"content": "   "}).encode(), json_headers(token_b))
        check("清空正文且无图时编辑被拒 400", s == 400, f"got {s}")
        http("DELETE", f"/api/posts/{plain['id']}", headers={"X-Auth-Token": token_b})
    else:
        check("清空正文且无图时编辑被拒 400", False, f"准备数据失败 {s}")

    # 改回原样，别影响后面的用例
    http(
        "PATCH",
        f"/api/posts/{post_id}",
        json.dumps(
            {
                "content": "东校区后街新开的螺蛳粉，汤头很足 🍜 #好吃到跺脚",
                "topic": "美食",
                "locationName": "广西民族大学东校区后街",
                "latitude": 22.7936,
                "longitude": 108.3651,
            }
        ).encode(),
        json_headers(token_a),
    )

    print("\n=== 3. 信息流与详情 ===")
    s, feed, _ = http("GET", "/api/posts?size=20", headers={"X-Auth-Token": token_a})
    check("拉取信息流", s == 200 and isinstance(feed.get("items"), list), f"got {s}")
    check("信息流里能找到刚发的动态", any(p["id"] == post_id for p in feed.get("items", [])))

    s, topic_feed, _ = http("GET", "/api/posts?topic=美食")
    check("按话题过滤", s == 200 and all(p["topic"] == "美食" for p in topic_feed.get("items", [])))

    s, detail, _ = http("GET", f"/api/posts/{post_id}")
    check("未登录也能看详情", s == 200 and detail["id"] == post_id, f"got {s}")

    print("\n=== 4. 评论 ===")
    s, c1, _ = http(
        "POST",
        f"/api/posts/{post_id}/comments",
        json.dumps({"content": "明天带我去！"}).encode(),
        json_headers(token_b),
    )
    check("用户 B 评论", s == 200 and c1.get("id"), f"got {s} {c1 if s != 200 else ''}")
    comment_id = c1.get("id")

    s, clist, _ = http("GET", f"/api/posts/{post_id}/comments")
    check("评论列表返回 1 条", s == 200 and len(clist) == 1, f"got {len(clist) if s == 200 else s}")
    check("评论作者正确", clist and clist[0]["author"]["nickname"] == nick_b)

    s, d, _ = http(
        "POST", f"/api/posts/{post_id}/comments", json.dumps({"content": "   "}).encode(), json_headers(token_a)
    )
    check("空评论被拒", s == 400, f"got {s}")

    s, d, _ = http("GET", "/api/posts/999999")
    check("不存在的动态返回 404", s == 404, f"got {s}")

    print("\n=== 5. 点赞（幂等）===")
    s, r, _ = http("POST", f"/api/posts/{post_id}/like", b"", {"X-Auth-Token": token_b})
    check("B 点赞成功", s == 200 and r.get("liked") and r.get("count") == 1, f"got {s} {r}")

    s, r2, _ = http("POST", f"/api/posts/{post_id}/like", b"", {"X-Auth-Token": token_b})
    check("重复点赞不叠加计数", s == 200 and r2.get("count") == 1, f"count={r2.get('count')}")

    s, detail2, _ = http("GET", f"/api/posts/{post_id}", headers={"X-Auth-Token": token_b})
    check("详情里 liked 状态为 true", detail2.get("liked") is True)

    s, r3, _ = http("DELETE", f"/api/posts/{post_id}/like", b"", {"X-Auth-Token": token_b})
    check("取消点赞后计数归零", s == 200 and r3.get("count") == 0, f"got {r3}")

    print("\n=== 6. 权限 ===")
    body = json.dumps({"content": "测试"}).encode()
    s, d, _ = http("POST", f"/api/posts/{post_id}/comments", body, {"Content-Type": "application/json"})
    check("未登录评论被拒 401", s == 401, f"got {s}")

    boundary, mb = multipart({"content": "没 token 想发"}, [])
    s, d, _ = http("POST", "/api/posts", mb, {"Content-Type": f"multipart/form-data; boundary={boundary}"})
    check("未登录发布被拒", s == 401, f"got {s}")

    s, d, _ = http("DELETE", f"/api/posts/{post_id}", b"", {"X-Auth-Token": token_b})
    check("删别人的动态被拒", s == 403, f"got {s}")

    s, d, _ = http("DELETE", f"/api/comments/{comment_id}", b"", {"X-Auth-Token": token_a})
    check("删别人的评论被拒", s == 403, f"got {s}")

    print("\n=== 7. 删除自己的 ===")
    s, d, _ = http("DELETE", f"/api/comments/{comment_id}", b"", {"X-Auth-Token": token_b})
    check("删除自己的评论", s == 200, f"got {s}")

    s, d, _ = http("DELETE", f"/api/posts/{post_id}", b"", {"X-Auth-Token": token_a})
    check("删除自己的动态", s == 200, f"got {s}")

    s, d, _ = http("GET", f"/api/posts/{post_id}")
    check("删后确实查不到", s == 404, f"got {s}")

    print("\n" + "=" * 46)
    print(f"结果：{passed} 通过 / {failed} 失败")
    print("=" * 46)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
