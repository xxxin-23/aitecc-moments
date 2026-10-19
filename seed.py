"""给本地环境灌几条演示动态，方便第一次打开时不至于空空如也。

运行前确保服务已起：mvn spring-boot:run
用法： python seed.py [base_url]
"""

import struct
import sys
import urllib.error
import urllib.request
import uuid
import zlib

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"


def gradient_png(width, height, top, bottom):
    """生成一张上下渐变的 PNG，纯标准库实现，不依赖 Pillow。"""

    def mix(ratio):
        return tuple(int(top[i] + (bottom[i] - top[i]) * ratio) for i in range(3))

    rows = b""
    for y in range(height):
        r, g, b = mix(y / max(1, height - 1))
        rows += b"\x00" + bytes([r, g, b]) * width

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(rows, 9))
        + chunk(b"IEND", b"")
    )


PALETTES = [
    ((255, 138, 128), (255, 209, 156)),
    ((129, 199, 255), (196, 232, 255)),
    ((255, 190, 118), (255, 236, 189)),
    ((149, 214, 175), (224, 247, 214)),
]

DEMO_POSTS = [
    {
        "nickname": "小螺蛳",
        "avatar": "🍜",
        "content": "东校区后街新开的新疆炒米粉，加了一份宽粉，分量足到吃不完。\n老板说辣椒是自己炒的，确实香！",
        "topic": "美食",
        "locationName": "广西民族大学东校区后街",
        "latitude": 22.7936,
        "longitude": 108.3651,
        "images": 3,
    },
    {
        "nickname": "图书馆常驻",
        "avatar": "📚",
        "content": "五楼靠窗的位置，下午四点的阳光刚好不刺眼。\n找到了期末复习的固定据点，谁也别跟我抢。",
        "topic": "学习",
        "locationName": "广西民族大学图书馆",
        "latitude": 22.7961,
        "longitude": 108.3633,
        "images": 1,
    },
    {
        "nickname": "不加冰",
        "avatar": "🥤",
        "content": "南门出去右转那家糖水铺，杨枝甘露给得实在，西柚粒铺满一层。",
        "topic": "探店",
        "locationName": "民大南门糖水铺",
        "latitude": 22.7901,
        "longitude": 108.3657,
        "images": 2,
    },
    {
        "nickname": "湖边的风",
        "avatar": "🌅",
        "content": "相思湖的落日，今天的云很好看。跑完步刚好赶上。",
        "topic": "风景",
        "locationName": "相思湖畔",
        "latitude": 22.7995,
        "longitude": 108.3602,
        "images": 1,
    },
]


def http(method, path, body=None, headers=None):
    req = urllib.request.Request(BASE + path, data=body, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        r = urllib.request.urlopen(req, timeout=40)
        raw = r.read()
        return r.status, raw.decode("utf-8", "ignore")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")


def multipart(fields, files):
    boundary = "----seed" + uuid.uuid4().hex
    out = b""
    for k, v in fields.items():
        out += (
            f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n'.encode()
            + str(v).encode("utf-8")
            + b"\r\n"
        )
    for field, filename, content in files:
        out += (
            f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; '
            f'filename="{filename}"\r\nContent-Type: image/png\r\n\r\n'.encode()
        )
        out += content + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return boundary, out


def main():
    urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
    created = 0

    for idx, spec in enumerate(DEMO_POSTS):
        s, raw = http(
            "POST",
            "/api/auth/register",
            f'{{"nickname":"{spec["nickname"]}","password":"demo123456","avatar":"{spec["avatar"]}"}}'.encode(),
            {"Content-Type": "application/json"},
        )
        # 昵称已存在就改用登录，重复运行脚本不会报错
        if s != 200:
            s, raw = http(
                "POST",
                "/api/auth/login",
                f'{{"nickname":"{spec["nickname"]}","password":"demo123456"}}'.encode(),
                {"Content-Type": "application/json"},
            )
        if s != 200:
            print(f"✗ 账号不可用：{spec['nickname']} -> {raw[:80]}")
            continue

        import json

        token = json.loads(raw)["token"]
        files = []
        n_text = "一二三四五"
        for i in range(spec["images"]):
            top, bottom = PALETTES[(idx + i) % len(PALETTES)]
            files.append(("images", f"demo_{idx}_{i}.png", gradient_png(240, 240, top, bottom)))
        _ = n_text

        boundary, body = multipart(
            {
                "content": spec["content"],
                "topic": spec["topic"],
                "locationName": spec["locationName"],
                "latitude": spec["latitude"],
                "longitude": spec["longitude"],
            },
            files,
        )
        s, raw2 = http(
            "POST",
            "/api/posts",
            body,
            {
                "Content-Type": f"multipart/form-data; boundary={boundary}",
                "X-Auth-Token": token,
            },
        )
        if s == 200:
            created += 1
            print(f"✅ [{spec['topic']}] {spec['nickname']}：{spec['content'].splitlines()[0][:26]}…")
        else:
            print(f"✗ 发布失败：{raw2[:100]}")

    print(f"\n完成：{created} 条动态。打开 {BASE} 看看效果吧")


if __name__ == "__main__":
    main()
