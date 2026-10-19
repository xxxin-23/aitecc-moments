# 🍃 民大朋友圈 · AITECC Moments

> 人工智能科技社内部版「朋友圈 / 小红书」：发图文动态、带定位打卡、评论点赞，把好吃好玩的地方分享给社团里的大家。

一个**零配置、开箱即跑**的 Spring Boot 单包应用 —— 不用装数据库、不用配 Nginx、前端也不用 npm 构建，
`mvn spring-boot:run` 一条命令就能跑起来。

---

## ✨ 已实现的功能

| 功能 | 说明 |
|------|------|
| **图文动态** | 一条动态最多 2000 字 + 9 张图，自动排成朋友圈 / 小红书的九宫格样式 |
| **定位打卡** | 浏览器自动取经纬度 + 手写地点名；点👇位置直接跳高德地图看在哪 |
| **话题分类** | 美食 / 探店 / 学习 / 运动 / 风景 / 吐槽 / 数码 / 其他，顶部横向标签过滤 |
| **评论** | 详情页评论列表，作者可以删自己的评论 |
| **点赞** | 幂等设计，重复调用不会重复计数；已点赞实时高亮 |
| **图片上传** | jpg / png / gif / webp / heic，单张 ≤ 10MB，**会真的解码校验**，改后缀的文本文件会被拒 |
| **轻量账号** | 昵称 + 密码 + emoji 头像，注册完直接进，不用二次登录 |
| **移动端 UI** | 手机优先布局，桌面自动居中成手机宽度；底部安全区适配 iPhone |

---

## 🚀 快速开始

要求：**JDK 17+**（本项目在 JDK 25 上验证通过）+ **Maven 3.6+**

```bash
git clone https://github.com/xxxin-23/aitecc-moments.git
cd aitecc-moments
mvn spring-boot:run
```

然后浏览器打开 <http://localhost:8080>

首次运行会在项目目录下自动生成 `data/`（数据库文件）和 `uploads/`（上传的图片），
两者都已在 `.gitignore` 里，**不会被提交进仓库**。

### 🧪 自测脚本 & 演示数据

```bash
python e2e_test.py     # 33 项端到端接口自测：注册登录/发图/评论/点赞/权限/删除
python seed.py         # 灌 4 条示例动态（带图 + 定位 + 话题），页面上立刻有东西看
```

`e2e_test.py` 里会顺带校验「伪造图片文件会被拒」「重复点赞不叠加」「删别人的内容报 403」
这些容易回归的场景，改动代码后跑一遍心里有底。

### 打包部署

```bash
mvn clean package -DskipTests
java -jar target/aitecc-moments.jar
```

部署到服务器时建议把 `application.yml` 里的上传目录改成绝对路径：

```yaml
moments:
  upload-dir: /var/www/aitecc-moments/uploads/
```

> ⚠️ **注意**：`navigator.geolocation` 浏览器定位只在工作在 **https** 或 **localhost** 下。
> 用 `http://服务器IP` 直接访问时自动定位会失败，此时会降级成「手填地名」。
> 正式对外开放请配 HTTPS（Nginx + Let's Encrypt 即可）。

---

## 🛠 技术选型

| 层 | 选型 | 为什么 |
|----|------|--------|
| 后端 | Spring Boot 3.5（Web + Data JPA + Validation） | 单体足够，一个 jar 跑完所有事 |
| 数据库 | H2 文件库（`./data/moments.mv.db`） | 零安装，社团规模够用；换 MySQL 只改 3 行配置 |
| 持久化 | Spring Data JPA + Hibernate | ddl-auto=update，加字段不用手写迁移 |
| 前端 | 原生 HTML + CSS + JS，**无构建步骤** | 不用 Node、不用打包，改完刷新即可见 |
| 口令 | PBKDF2-HMAC-SHA256 + 每用户独立随机盐 | JDK 内置，不引额外依赖；明文从不落库 |

### 想换成 MySQL？

改 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aitecc_moments?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: 你的账号
    password: 你的密码
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

再在 `pom.xml` 里加：

```xml
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <scope>runtime</scope>
</dependency>
```

也可以问我加上 Postgres，改动量同样很小。

---

## 📁 目录结构

```
aitecc-moments/
├── pom.xml
├── src/main/java/com/aitecc/moments/
│   ├── MomentsApplication.java          # 启动类
│   ├── config/WebConfig.java            # CORS + /uploads 静态资源映射
│   ├── dto/{Auth,Feed}.java             # 请求/响应 record
│   ├── model/                           # JPA 实体：User Post PostImage Comment Like
│   ├── repository/                      # Spring Data 接口
│   ├── service/                         # 业务逻辑
│   │   ├── AuthService.java             # 注册/登录/令牌
│   │   ├── PostService.java             # 发布/信息流/删除
│   │   ├── SocialService.java           # 评论/点赞（幂等）
│   │   ├── FileStorage.java             # 图片落盘 + 类型校验
│   │   ├── PasswordHasher.java          # PBKDF2 口令散列
│   │   └── BizException.java            # 业务异常 → JSON 错误
│   └── web/                             # Controller + 全局异常处理
└── src/main/resources/
    ├── application.yml
    └── static/{index.html, app.js, style.css}
```

---

## 📡 API 一览

统一规则：需要登录的接口在请求头带 `X-Auth-Token: <token>`；
错误响应统一是 `{"error": "原因", "status": 400, "timestamp": "..."}`。

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|------|
| POST | `/api/auth/register` | 注册，返回 `{token, user}` | — |
| POST | `/api/auth/login` | 登录 | — |
| GET | `/api/auth/me` | 当前登录者 | — |
| POST | `/api/auth/logout` | 退出（服务端清令牌） | ✅ |
| PATCH | `/api/auth/profile` | 改头像 / 简介 | ✅ |
| GET | `/api/posts` | 信息流，`?page=&size=&topic=&userId=` | 可选 |
| GET | `/api/posts/topics` | 话题列表 | — |
| GET | `/api/posts/{id}` | 单条详情 | 可选 |
| POST | `/api/posts` | 发布（multipart：`content` `topic` `locationName` `latitude` `longitude` `images[]`） | ✅ |
| DELETE | `/api/posts/{id}` | 删自己的动态 | ✅ |
| GET | `/api/posts/{id}/comments` | 评论列表 | 可选 |
| POST | `/api/posts/{id}/comments` | 评论 `{content}` | ✅ |
| DELETE | `/api/comments/{id}` | 删自己的评论 | ✅ |
| POST | `/api/posts/{id}/like` | 点赞，返回 `{liked, count}` | ✅ |
| DELETE | `/api/posts/{id}/like` | 取消点赞 | ✅ |

---

## 🔒 安全说明

- 口令用 PBKDF2 加盐存储，**明文从不写进数据库**；比对用定长时间比较，抗计时侧信道
- 上传图片不只信 `Content-Type`，还会 `ImageIO` 实际解码一遍再放行
- 前端所有用户内容都走 `esc()` 转义后才进 DOM，**防止 XSS**（昵称、正文、评论都算不可信输入）
- CORS 仅放行本地开发源，部署上线记得把 Nginx 域名加进 `WebConfig`
- 未处理的异常只上报的完整堆栈进日志，响应里只返回「服务器开小差了」

---

## 🗺 后续可以做的事

- [ ] **换 MySQL + 定期备份**（社团长期用建议做这一步）
- [ ] 图片传对象存储（COS/OSS），解放服务器磁盘
- [ ] 接微信登录 / 学号认证，杜绝校外人员乱入
- [ ] 内容审核（关键词过滤 + 举报），社团群里人多不免有广告
- [ ] 私信 / 关注机制，做成真·社交产品
- [ ] 图片压缩与 WebP 转换，省流量

---

## 🛠 推送方式

常规方式当然是：

```bash
git add -A && git commit -m "..." && git push
```

**但如果你也遇到 `git push` 连不上**（代理报 `CONNECT tunnel failed`、或 `Failed to connect to github.com:443`），
可以用仓库里自带的 `push_repo.py` —— 它走 GitHub REST API 提交，绕开 git 的网络层：

```bash
python push_repo.py   # 把本地 git 已跟踪的文件整体推到 main
```

它会保留远端已有但本地没有的文件（比如 LICENSE），不会把别人的东西冲掉。
个人访问令牌放在 `~/.github_pat.txt`，脚本内部读取，不经过命令行。

---

## ℹ️ 关于仓库归属

本项目目前托管在个人账号 `xxxin-23/aitecc-moments`。
GitHub 组织 `GXMZU-AITECC` 设置了「普通成员不可创建仓库」，
后续要转进社团名下，需要组织管理员（`xwms` / `isryanyhliu` / `kelly-624` / `LCY261`）
建仓后我再提 PR 迁移，或由 owner 直接在 Settings → Transfer ownership 接收。

---

MIT License.
