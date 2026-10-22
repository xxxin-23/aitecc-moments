# 🎀 科技社陪着你走过青葱校园 · AITECC Moments

> 人工智能科技社内部版「朋友圈 / 小红书」：**科技社陪着你走过青葱校园**。
> 粉嫩 Hello Kitty 风格，发图文动态、带定位打卡，和同学互相**点赞 · 评论 · 收藏 · 推荐**，
> 把好吃好玩的地方分享给社团里的大家。

一个**零配置、开箱即跑**的 Spring Boot 单包应用 —— 不用装数据库、不用配 Nginx、前端也不用 npm 构建，
`mvn spring-boot:run` 一条命令就能跑起来。

---

## ✨ 已实现的功能

| 功能 | 说明 |
|------|------|
| **图文动态** | 一条动态最多 2000 字 + 9 张图，自动排成朋友圈 / 小红书的九宫格样式 |
| **编辑动态** | 自己发的可以随时改文字 / 话题 / 定位（图片不动，要换图就删了重发） |
| **定位打卡** | 浏览器自动取经纬度 + 手写地点名；点👇位置直接跳高德地图看在哪 |
| **话题分类** | 美食 / 探店 / 学习 / 运动 / 风景 / 吐槽 / 数码 / 其他，顶部横向标签过滤 |
| **💬 评论** | 详情页评论列表，作者可以删自己的评论 |
| **❤️ 点赞** | 幂等设计，重复调用不会重复计数；已点赞实时高亮 |
| **⭐ 收藏** | 看到想慢慢看的点收藏，「我的收藏」里随时翻出来 |
| **🌟 推荐** | 真心想安利给同学的就点推荐 —— 推荐票权重最高，会把它顶上「推荐」流 |
| **🌟 推荐 / 🕊️ 最新 / ⭐ 我的收藏** | 三个信息流：按热度（点赞+评论+收藏+推荐，带时间衰减）、按时间、只看自己收藏的 |
| **改昵称 / 头像** | 点右上角头像进「我的资料」，昵称、emoji 头像、一句话简介都能改，改完旧动态署名同步更新 |
| **多人实时更新** | 大家共用同一份数据：信息流每 15 秒自动拉一次，别人发的新内容会冒出「✨ 有 N 条新动态」提示条（点一下就插到顶部），点赞 / 评论 / 收藏 / 推荐数就地更新；详情页评论每 8 秒刷新一次。页面切到后台或你正在写东西时不打扰，切回来立刻拉最新 |
| **图片上传** | jpg / png / gif / webp / heic，单张 ≤ 10MB，**会真的解码校验**，改后缀的文本文件会被拒（iPhone 的 heic 直接放行） |
| **轻量账号** | 昵称 + 密码 + emoji 头像，注册完直接进，不用二次登录、不用邀请码 |
| **移动端 UI** | 手机优先布局，桌面自动居中成手机宽度；底部安全区适配 iPhone；Hello Kitty 粉嫩主题 |

### 🌟 「推荐」流是怎么排的

热度分 = `点赞×1 + 评论×2 + 收藏×2 + 推荐×3`，再除以「发布至今小时数 + 2」的 0.6 次方做时间衰减。
也就是说：**刚发出来、又被大家真心推荐的内容排最前**，老内容会自然沉下去，不会永远是那几条。
权重写在 `PostService` 顶部的常量里，想调随时改。

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

### 🌐 让社团同学也能打开（局域网共享）

应用默认已监听 `0.0.0.0`（见 `application.yml` 的 `server.address`），
只差 Windows 防火墙放行 8080 端口：

```bash
java -jar target/aitecc-moments.jar --server.port=8080 --server.address=0.0.0.0
```

然后用**管理员身份**运行仓库里自带的 `open-lan-access.ps1`（右键 → 使用 PowerShell 运行），
它会自动加防火墙入站规则，并打印出可以发给同学的地址，例如：

```
http://192.168.3.36:8080
```

连着同一个 WiFi / 校园网的同学直接点开就能用。**要保持这台机器开机且应用不关。**

> 不想每次都手动放行，也可以在管理员 PowerShell 里手动执行：
> `New-NetFirewallRule -DisplayName "AITECC Moments 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow -Profile Any`

### 👥 谁能看、谁能发

| 角色 | 浏览动态 / 评论 | 发布 / 评论 / 点赞 |
|------|:---:|:---:|
| 未登录访客 | ✅（由 `moments.allow-anonymous-read` 控制） | ❌ |
| 注册成员（昵称 + 密码 + emoji 头像，3 秒完成） | ✅ | ✅ |

也就是说：**任何人点开链接就能看到大家在发什么**，想发言注册一下就行，
不需要邀请码、不需要管理员审批 —— 适合社团内部快速用起来。
如果后面要限制校外人员，可以做学号认证 / 邀请码注册（见「后续可以做的事」）。

> ⚠️ 局域网 HTTP 下浏览器的「自动定位」会失败（浏览器只在 https / localhost 下给定位权限），
> 此时会降级成**手填地名**，不影响使用。要完整的定位打卡体验请配 HTTPS。

### 🔑 仓库权限：科技社成员可查看 + 可编辑

组织 `GXMZU-AITECC` 的**全体成员都已被加为仓库协作者（write）**，也就是：

| 身份 | 查看 | 提 Issue / 发帖 | 推代码、处理 PR |
|------|:--:|:--:|:--:|
| 科技社成员（组织内） | ✅ | ✅ | ✅ |
| 其他 GitHub 用户 | ✅ | ✅ | ❌（可 fork 后提 PR） |

仓库是 **public** —— 内容对外可见，但**只有组织成员能直接改**。
改代码前请看 [CONTRIBUTING.md](CONTRIBUTING.md)（先开 Issue 认领，再 `feature/xxx` 分支 → PR → review）。

> 仓库设置、协作者管理、删库这类操作只有 owner（`xxxin-23`）能做，请别动。
> 想长期挂社团名下，需要组织管理员（`xwms` / `isryanyhliu` / `kelly-624` / `LCY261`）
> 在 Settings → Transfer ownership 接收 —— 个人账号无法自己完成转移。

### 🧪 自测脚本（线上环境不含任何演示数据）

```bash
python e2e_test.py     # 44 项端到端接口自测：注册登录/发图/评论/点赞/编辑/权限/删除
```

`e2e_test.py` 里会顺带校验「伪造图片文件会被拒」「重复点赞不叠加」「删别人的内容报 403」
这些容易回归的场景，改动代码后跑一遍心里有底。

> **平台里没有任何预置的示例动态** —— 同学打开就是干净的首页，第一条内容由大家自己发。
> 自测脚本会造数据，建议像下面这样**起一个临时实例**跑，不要污染正式库：
>
> ```bash
> java -jar target/aitecc-moments.jar --server.port=8081 \
>   --spring.datasource.url='jdbc:h2:file:./tmp-test-data/test;MODE=MySQL;DB_CLOSE_DELAY=-1' \
>   --moments.upload-dir=./tmp-test-uploads/
> python e2e_test.py http://127.0.0.1:8081
> # 跑完删掉 tmp-test-data / tmp-test-uploads 两个目录即可
> ```
>
> 万一以前跑过 `seed.py` 灌了演示数据，用 `python clean_demo.py` 可以一键清干净（含图片文件）。

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
│   ├── model/                           # JPA 实体：User Post PostImage Comment Like Reaction
│   ├── repository/                      # Spring Data 接口
│   ├── service/                         # 业务逻辑
│   │   ├── AuthService.java             # 注册/登录/令牌/改资料
│   │   ├── PostService.java             # 发布/信息流（推荐+最新+收藏）/编辑/删除
│   │   ├── SocialService.java           # 评论/点赞/收藏/推荐（全部幂等）
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
| PATCH | `/api/auth/profile` | 改昵称 / 头像 / 简介（传哪个改哪个，昵称撞名会 400） | ✅ |
| GET | `/api/posts` | 信息流，`?page=&size=&topic=&userId=&sort=recommend｜latest`（默认 recommend） | 可选 |
| GET | `/api/posts/topics` | 话题列表 | — |
| GET | `/api/posts/{id}` | 单条详情 | 可选 |
| POST | `/api/posts` | 发布（multipart：`content` `topic` `locationName` `latitude` `longitude` `images[]`） | ✅ |
| PATCH | `/api/posts/{id}` | 编辑自己的动态（JSON：`content` `topic` `locationName` `latitude` `longitude`），只改文字/话题/定位 | ✅ |
| DELETE | `/api/posts/{id}` | 删自己的动态 | ✅ |
| GET | `/api/posts/{id}/comments` | 评论列表 | 可选 |
| POST | `/api/posts/{id}/comments` | 评论 `{content}` | ✅ |
| DELETE | `/api/comments/{id}` | 删自己的评论 | ✅ |
| POST | `/api/posts/{id}/like` | 点赞，返回 `{liked, count}` | ✅ |
| DELETE | `/api/posts/{id}/like` | 取消点赞 | ✅ |
| POST | `/api/posts/{id}/favorite` | 收藏，返回 `{on, count}` | ✅ |
| DELETE | `/api/posts/{id}/favorite` | 取消收藏 | ✅ |
| POST | `/api/posts/{id}/recommend` | 推荐给同学，返回 `{on, count}` | ✅ |
| DELETE | `/api/posts/{id}/recommend` | 取消推荐 | ✅ |
| GET | `/api/me/favorites` | 我的收藏列表，结构和信息流一致 | ✅ |
| GET | `/api/me/stats` | 我收藏过 / 推荐过多少条 | ✅ |

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

## 📮 仓库协作区（不用装环境也能参与）

仓库是 **public**，以下入口对社团成员全部开放，有 GitHub 账号即可发言：

| 入口 | 用来做什么 | 地址 |
|------|-----------|------|
| **💬 Discussions** | 社团论坛：分享好吃好玩、提想法、提问互助、灌水。支持发帖 + 楼中楼回复 + 贴图 | `仓库 → Discussions` |
| **🐛 Issues** | 提 Bug、提功能需求、认领开发任务 | `仓库 → Issues` |
| **📖 Wiki** | 使用说明、部署文档、社团规范沉淀 | `仓库 → Wiki` |
| **📋 Projects** | 任务看板，排开发优先级 | `仓库 → Projects` |

Discussions 自带 6 个板块，用法建议：

| 板块 | 适合发什么 |
|------|-----------|
| `Announcements` | 社团活动、招新、比赛通知 |
| `Show and tell` | 探店 / 游玩分享，带图和定位（对应平台的「好吃好玩」） |
| `Ideas` | 对平台的功能建议 |
| `Q&A` | 使用中遇到问题、技术求助 |
| `Polls` | 投票（比如周末去哪聚餐） |
| `General` | 其他闲聊 |

> 平台（Web 应用）负责**日常刷动态**，仓库 Discussions 负责**没开电脑时也能异步讨论**，
> 两者互补。不想部署服务器的同学，直接把仓库当论坛用也是可以的。

---

## ℹ️ 关于仓库归属

本项目目前托管在个人账号 `xxxin-23/aitecc-moments`。
GitHub 组织 `GXMZU-AITECC` 设置了「普通成员不可创建仓库」，
后续要转进社团名下，需要组织管理员（`xwms` / `isryanyhliu` / `kelly-624` / `LCY261`）
建仓后我再提 PR 迁移，或由 owner 直接在 Settings → Transfer ownership 接收。

---

MIT License.
