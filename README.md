# 摸摸鱼 · 漫剧

Vue 3 + Element Plus 前端与 Java 17 / Spring Boot 3 后端分离的免登录视频站。支持浏览、搜索、分类、排序、片单、观看记录、分集接口、MP4/HLS 播放、画质切换、倍速与断点续播。

**当前默认接入 360 资源的实时 AI 漫剧目录，支持搜索、自动完整载入片库与全集播放；无尽漫剧为可切换来源。非凡适配器已实现；测试中曾出现403，随后恢复，但“漫剧”检索目前无结果。**

全集合并版明确标注为“全集版”：例如上游标记“全60集（合全集）”的作品，显示原作60集，但播放器只提供真实的一个合集地址，不虚构60个选集。独立分集源则按实际列表显示。目录排序依据更新顺序；不编造热度、评分或播放量。

所有公开查询与播放接口均不要求登录，不包含会员购买或解锁流程。观看记录与片单仅保存在当前浏览器。视频从相应片源直接播放，所在地区的网络会影响速度。

## 本地启动

需要 Node.js 22.12+、Java 17+。提供 Maven Wrapper，也可使用 Maven 3.6.3+。

终端一：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

终端二：

```powershell
cd frontend
npm ci
npm run dev
```

访问 http://127.0.0.1:5173 。后端端口 8080；Vite 将 `/api` 请求代理到后端。首次安装依赖需要联网。

未提供本机数据库配置时，Spring Boot 自动使用内存 H2，启动后创建表并导入开放短片备用目录；实时漫剧通过 Java 适配器请求公开接口，以120秒缓存减少重复调用，无需安装 MySQL。需要持久化时自行配置本机数据库；已有数据库不会在重启时被初始目录覆盖。

本机配置不进入版本库。如需持久化数据库，创建 `backend/src/main/resources/application-local.yml`，填入你自己的开发数据库连接、驱动及凭据，并按需设置 `spring.sql.init.mode` 和 `app.cors-origins`。项目支持 H2 与 MySQL；生产使用环境变量配置。Vite 与 Nginx 代理均使用同源 API，无需启用跨域；独立域名部署时明确配置允许的来源。

## 构建与验证

```powershell
cd backend
.\mvnw.cmd verify
cd ../frontend
npm run lint
npm run build
npm exec playwright test
```

浏览器测试前请先启动前后端，需要本机 Google Chrome，或在 `playwright.config.js` 中配置已经安装的浏览器。视频测试使用真实源站，网络较慢时可能超时。页面错误处理测试会模拟片源不可用。

## 生产部署

前端构建目录为 `frontend/dist`；Nginx 的配置样例在 `deploy/nginx.conf`，它将 `/api/` 转发至 Java 服务，并支持 Vue 路由刷新。

360封面由本站 `/api/dramas/{id}/poster` 读取与缓存，访客无需连接外站7788端口；只接收已知片源的剧集ID，不能用来代理任意URL。

站名为摸摸鱼。浏览器图标、站标和可收起的快捷挂件使用三头身Q版大肥鱼形象；搜索、分类、片单、观看记录和播放等控件使用九组不同动作的透明图标，保留原功能符号与中文名称。影院配色保持不变。首页推荐每6秒循环切换，悬停、键盘焦点和后台标签页会暂停，支持手动暂停及减少动态效果偏好。

Java 后端的生产配置只读取环境变量，使用 MySQL。部署前创建数据库 `pianke`，设置：

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://数据库主机:3306/pianke?characterEncoding=utf8
DB_USERNAME=数据库用户
DB_PASSWORD=通过部署环境注入
CORS_ORIGINS=https://你的网站域名
```

`application-prod.yml` 不含密码默认值。自动建表文件兼容 H2 与 MySQL；生产 MySQL 配置提供完毕后再启用 prod。当前工作环境只验证了本地 H2，未运行外部 MySQL 或生产部署。

## API

| 请求 | 用途 |
| --- | --- |
| `GET /api/health` | 服务状态 |
| `GET /api/dramas?keyword=&category=&sort=featured&source=zy360&page=1` | 实时目录；source 支持 zy360 / wj / ff，open 为开放短片；支持分页与最新排序 |
| `GET /api/dramas/{id}` | 剧集详情与来源 |
| `GET /api/dramas/{id}/episodes` | 分集列表 |
| `GET /api/dramas/{id}/episodes/{number}/play` | 当前集真实媒体地址与线路标签；不返回解析网页 |

统一响应为 `code/message/data`。参数错误返回 HTTP 400，作品或集数不存在返回 404。接口是公开只读接口，没有开放数据库写入或任意地址视频代理。

## 片源适配与内容边界

`MaccmsCatalogService` 接入三个固定的 MacCMS 公开端点，仅提取 MP4 / M3U8 媒体地址，不需要访客登录或运营方密钥。不接受任意用户传入的接口 URL。列表和详情缓存120秒。客户端自动在后台载入当前片源的全部匹配目录，并以两路请求限制并发、进行去重与短期复用；卡片按滚动位置逐批渲染，封面原生懒加载，不再需要点击“加载更多”。

- 默认360源以“AI”检索，并再次检查漫剧标记，排除普通电影等无关结果。
- 无尽源使用其“漫剧”分类63。分类名称只表明漫剧，不保证每部都由AI制作。
- 非凡查询曾有间歇403；最近“漫剧”检索返回空目录，不混入仅标题含AI的普通影视。
- 类型筛选由片源标题关键词整理，不构造不存在的来源评分、热度或时长。
- 第三方目录的公开访问与内容发行授权是不同事项；本站保留来源，未将这些内容标为CC作品。

`backend/src/main/resources/catalog.json` 另保留9部开放授权短片作为备用库，通过 `source=open` 查询。此备用目录不会混入默认漫剧首页。可用 `python scripts/sync-catalog.py` 刷新开放片源；已有数据库不会被该文件或重启静默覆盖。

验证工具 `python scripts/check-manga-sources.py` 会检查三个接口及样本的播放清单、首尾分片和跨域响应。它不下载完整影片。接口状态可能随时间、地区和上游变化。

## 目录

- `frontend/src/pages`：发现、片单、观看记录、播放页面。
- `frontend/src/api`：前后端请求边界。
- `backend/src/main/java/cn/pianke`：Controller → Service → Mapper 分层。
- `backend/src/main/resources`：配置、数据库表、初始真实目录。
- `THIRD_PARTY_NOTICES.md`：内容、字体和开源库来源。
