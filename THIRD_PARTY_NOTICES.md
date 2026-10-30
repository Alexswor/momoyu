# 第三方资源

## 开源依赖

Vue、Vue Router、Element Plus、Anime.js、HLS.js 为 npm 依赖，版本固定在 `frontend/package-lock.json`，各自的许可证随相应软件包提供。本项目未复制 MotionSites 的付费模板或 Showreel.Design 上的影片。

设计参考：[MotionSites](https://motionsites.ai/)、[Showreel.Design](https://showreel.design/)、[Uiverse Galaxy](https://github.com/uiverse-io/galaxy)、[Anime.js](https://github.com/juliangarnier/anime)。采用逐字入场、推荐缩略片带、按钮扫光与卡片渐入交互。

## 鲸鱼娘元素

按用户指定的 [TreapGoGo/deepseek-whale-girl](https://github.com/TreapGoGo/deepseek-whale-girl) 中 `characters/dafeiyu/reference-sheets/dafeiyu-chibi-character-sheet.png` 为编辑目标，使用内置图像工具提取正面全身透明底素材，保留三头身、长发、女仆裙和鲸尾。项目素材为 `frontend/public/whale/dafeiyu-chibi.png`；用于浏览器图标、站标、快捷挂件和占位图。该参考项目为非官方社区资源库，不代表本站与 DeepSeek 官方有关联。

来源项目将网站代码与图片素材分别说明；保留其 LICENSE 不代表将角色图片重新标记为 MIT。参考图及提取结果的权利说明按来源项目说明。影院背景、黄绿色强调色与主体布局未改变。功能图标位于 `frontend/public/whale/icons/`，以同一角色为参考经内置图像工具生成九组动作，涵盖搜索、电影打板、施法、举闪电、使用手机、读卷轴、抱爱心、举时钟和指路。仅作 WebP 尺寸与透明度优化；不改变影院主题色。

## 圆润字体

寒蝉全圆体 ChillRoundF，来自 [Warren2060/ChillRound](https://github.com/Warren2060/ChillRound)，使用 SIL Open Font License 1.1。原始许可证完整保留于 `frontend/public/fonts/OFL.txt`。字体本地托管，不依赖访客连接外部字体服务。

## 第三方漫剧目录

用户指定参考非凡、360、无尽采集接口。默认360资源，另支持无尽漫剧分类和非凡适配器。视频与海报从对应公开来源加载，本站不声明拥有其著作权，也没有将其标为开放授权。来源查询链接保留在播放页。当前默认目录与下述开放短片库相互独立。

- 360：<https://360zy.com/api.php/provide/vod/>
- 无尽：<https://api.wujinapi.me/api.php/provide/vod/>
- 非凡：<https://api.ffzyapi.com/api.php/provide/vod/>；[接入说明](https://ffzy.tv/help/)

## 备用开放短片与封面

视频未修改，通过官方公开视频地址播放。封面来自同一作品的官方预览图，只在页面展示时以 CSS 裁切与叠加中文标签；原作片名及作者在播放页授权详情中保留。本项目不表示官方对本站作出背书。

| 中文展示名 | 原作 / 作者 | 授权 | 官方来源 |
| --- | --- | --- | --- |
| 春日 | Spring / Andy Goralczyk, Blender Animation Studio | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | [原作](https://video.blender.org/w/3d95fb3d-c866-42c8-9db1-fe82f48ccb95) |
| 羊驼与企鹅 | Caminandes 3: Llamigos / Pablo Vazquez, Blender Institute | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | [原作](https://video.blender.org/w/23f3ef79-15dc-44c5-aa45-cf92e78a4509) |
| 充电 | Charge / Hjalti Hjalmarsson, Blender Studio | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | [原作](https://video.blender.org/w/04da454b-9893-4184-98f3-248d00625efe) |
| 辛特尔 | Sintel / Colin Levy, Blender Foundation | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | [原作](https://video.blender.org/w/0eb052d0-fd51-43e6-aa33-ecdbf77a5d40) |
| 咖啡之旅 | Coffee Run / Hjalti Hjalmarsson, Blender Animation Studio | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | [原作](https://video.blender.org/w/ff8fe61b-026f-4f07-b66b-2a790d6f6ab1) |
| 大雄兔 | Big Buck Bunny / Sacha Goedegebure, Blender Foundation | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | [原作](https://video.blender.org/w/bf1f3fb5-b119-4f9f-9930-8e20e892b898) |
| 钢铁之泪 | Tears of Steel / Ian Hubert, Blender Foundation | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | [原作](https://video.blender.org/w/8533ea43-4271-4a57-9694-e9d0b35e1aa1) |
| 大象之梦 | Elephants Dream / Bassam Kurdali, Orange Open Movie Project | [CC BY 2.5](https://creativecommons.org/licenses/by/2.5/) | [原作](https://video.blender.org/w/cccc3e60-0291-4ecc-aa56-39b2e2c7d0d5) |
| 羊驼大冒险 | Caminandes 2: Gran Dillama / Pablo Vazquez, Blender Foundation | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | [原作](https://video.blender.org/w/fb70d459-48d2-4db5-adba-813c84f9200a) |

两部 Caminandes 作品在本站各自作为一个完整故事展示，不声称收录了原系列所有三部。具体授权以各原作者作品页面及片尾声明为准。
