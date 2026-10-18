# 架构说明

## 1. 为什么是「三层 + 一条数据流」

原 HTML 是**一张页面 = 一份试卷**：数据内嵌在 `window.__PAPER__`，功能写死在 1400 行 DOM 操作里。要「通用」，必须把**内容**和**渲染**彻底拆开：

```
① 内容层  content/<id>/paper.json     人/AI 可读可改，单一事实源
② 编译层  framework                   校验 + 编译成 App 消费的包（.epak）
③ 展示层  Android App                 数据驱动渲染，不含任何具体试卷的内容
```

App 里没有任何一句「德阳五中」的硬编码。它只认 `.epak` 包格式。

## 2. 数据契约（canonical）

一份试卷 = 1 个 JSON（或 YAML），结构定义见 `framework/src/schema.mjs`（可用 `exam schema` 打印完整 JSON Schema）。

```jsonc
{
  "schemaVersion": 1,
  "id": "deyang-2025-10-english",     // 小写字母数字连字符，App 里试卷主键
  "meta": { "title": "...", "sub": "...", "full": "...", "paperLine": "满分 150 分 · 时间 120 分钟" },
  "sections": [                        // 一篇 = 一个 section
    {
      "id": "A", "type": "read",       // read   seven   cloze   gram
      "tabLabel": "阅读理解", "chip": "A · 21–23",
      "title": "阅读理解 A 篇", "passageTitle": "Four Great Online Art Classes for Kids",
      "topic": "四类儿童在线美术课（应用文）",
      "qnos": ["21","22","23"],
      "paras": [ { "heading": "...", "en": "... {{41}} ...", "zh": "..." } ],
      "vocab": [ { "word": "affordable", "phonetic": "/əˈfɔːdəbl/", "partOfSpeech": "adj.", "zh": "负担得起的" } ],
      "migrations": [ { "title": "写作迁移点", "detail": "..." } ],
      "outline": [ { "level": "l1", "text": "..." } ],
      "questions": [ {
        "no": "21", "stem": "...", "kind": "细节理解题",
        "answer": { "letter": "C", "text": "Prepare art supplies.", "zh": "（准备美术用品）", "hint": null },
        "options": [ { "label": "A", "text": "Pay tuition fees." } ],
        "analysis": {
          "solutionLabel": "正确选项理由",
          "solutions": [ { "label": "原文依据（第 2 段）", "text": "...", "loc": "..." } ],
          "groups": [ { "key": "distract", "label": "干扰项排除", "items": ["..."] } ]
        }
      } ]
    }
  ],
  "dict": [ { "word": "afford", "phonetic": "...", "partOfSpeech": "v.", "zh": "..." } ],
  "quiz": { "title": "...", "total": 100, "parts": [ ... ] },
  "mindmaps": [ { "sectionId": "A", "file": "mindmaps/A.svg" } ]
}
```

**空位约定**（重要）：挖空题的英文原文里用占位标记，编译时转成可渲染的空位：

  写法   含义  
 --- --- 
  `{{41}}`   第 41 空，无提示词（完形 / 七选五）  
  `{{56 it}}`   第 56 空，所给提示词 `it`（语法填空）  
  `{{ {{56 }}  it}}`   旧版 HTML 的历史写法，抽取时自动归一  

**行内富文本**：解析文本里可以用 `<span class="ans">`（正确项，红）、`<span class="q">`（考点短语，橙）、`<b>` / `<i>` / `<br>`。编译段段统一转成 segments，App 不解析 HTML。

## 3. 编译产物：`.epak`

一个 zip（STORE，不压缩，方便零依赖生成）：

```
manifest.json    { format:"epak", formatVersion:1, id, title, full, builtAt, generator, counts }
exam.json        compiled 试卷：所有文本已 segments 化，id 全部补齐
mindmaps/A.svg   篇目思维导图
```

`exam.json` 与 canonical 的差别：只多了**渲染需要的东西**（`id`、`index`、`program segments`），不丢内容。

## 4. SQLite（Room）表

  表   作用  
 --- --- 
  `papers`   试卷库：id/标题/满分说明/来源/导入时间/统计  
  `sections`   篇目：题型、标签、题号、结构导图 JSON、写作迁移 JSON、导图路径  
  `paragraphs`   段落：英文 segments、中文 segments、小标题  
  `questions`   题目：题干 segments、答案 JSON、选项 JSON、解析 JSON  
  `section_vocab`   每篇重点词  
  `dict_entries`   整卷查词词典（点词/搜索用）  
  `quiz_parts` / `quiz_items`   词汇检测卷：选词框、短文（已挖空）、题目  
  `study_state`   每题：选了哪项 / 填了什么 / 是否看过答案  
  `quiz_answers`   词卷作答与判分结果  
  `wordbook`   生词本  
  `sentences`   收藏句  
  `settings`   字号 / 千问 API Key（识别+翻译共用）  
  `import_log`   导入记录（排障用）  

所有子表对 `papers` 声明 `ON DELETE CASCADE`，删试卷即清空关联数据。

## 5. 页面结构（全部数据驱动）

  页面   说明  
 --- --- 
  试卷库   多份试卷列表；导入 `.epak`（文件选择器 / 文件管理器「打开方式」）；长按或按钮删除  
  试卷目录   试卷头信息 + 篇目卡片 + 入口（词汇检测 / 生词本 / 收藏句 / 设置）  
  篇目学习   三个页签：**原文**（段落、译文开关、查词、重点词）、**题目**（作答、显示答案、逐项解析）、**拓展**（结构导图 / 写作迁移 / 思维导图）。宽屏（≥900dp）自动左右双栏，窄屏「对照原文」抽屉  
  词汇检测   按部分切换，自动识别「音标写词 / 挖空句选词 / 短文选词填空」三种题型；提交判分、显示答案、重做  
  生词本 / 收藏句   列表 + 移除  
  设置   字号 80–160%；只填 1 个千问 Key（识别+翻译）；导入包、清空作答、删除试卷  

### 通用性怎么保证

- 题型由 `type` + 数据结构决定：`options` 非空 → 选择题；为空 → 填空题。
- 任何 `outline` / `migrations` / `vocab` / `mindmap` 为空就整块不渲染，不写死某篇。
- 词卷三种形态靠字段判断：`items[].phonetic/zh` → 写词；`items[].pre` → 挖空选词；`segments/answers` → 短文填空。
- 新增题型只需扩展 schema 的 `type` 枚举 + 一个渲染分支，内容无需改结构。

## 6. Web 预览（framework 的第二个 target）

`exam build --target web` 用同一份 compiled 数据生成**单文件静态 HTML**：篇目切换、原文/译文、逐题显示答案与解析。用途：

- 内容校对（AI 扫完立刻肉眼验收，不用装 APK）
- 老师投屏/打印前的快速检查
- 没有 Android 设备时的兜底查看

## 7. App 内出卷管线（老师自己用，不依赖电脑）

```
选图(Uri[]) ──▶ VisionClient.vision(settings)
                    │  base64 压缩到 1600px 长边，逐张请求，OpenAI 兼容 /chat/completions
                    ▼
              KExam（canonical，字符串形态：{{41}} 空位、<span class="ans"> 标记、整段中文）
                    │  DraftMapper.fromCanonical
                    ▼
              DraftModel（Compose 可观察的可变草稿）
                    │  老师只改三类：原文 en / 判解 solutions+groups / 翻译 zh
                    │  「译」按钮 → TranslateService（阿里云 MT，没配则大模型兜底）
                    ▼
              DraftValidator（题号重复 / 选择题缺答案 / 原文空段 / 空位与题号对账）
                    │  DraftMapper.toCompiled —— Inline.parse 把字符串编译成 segments
                    ▼
              CExam（compiled）
                    │  EpakWriter.build —— manifest.json + exam.json + mindmaps/*.svg（STORE zip）
                    ▼
              ExamRepository.importEpak —— 与导入外部包同一条路径 → Room/SQLite
```

要点：

- **`.epak` 是自动生成的中间产物**，老师看不到也不需要理解；目的只是与电脑端/framework 保持同一种格式，方便互相搬运。
- **编辑范围被刻意收窄**：只有 `原文 / 讲解 / 翻译` 可写；`options` 与 `answer` 只读。老师不会把答案改错，学生端答案始终可信。
- **空位一致性**：`DraftSection.blankCheck()` 在编辑器里直接提示「原文有 41 但没对应题目」这类问题。
- **重导入即覆盖**：同 `id` 保存会先删旧试卷（连带作答痕迹），避免题目与作答错位。
- **思维导图**：编辑已有试卷时，旧 SVG 从 `filesDir/mindmaps/<paperId>/<sid>.svg` 读回，保存时原样重新打进包。

## 8. 两条产线的关系

    App 内出卷   framework / MCP  
 --- --- --- 
  使用者   英语老师   开发者 / 批量场景  
  输入   手机相册里的试卷照片   电脑上的图片 / 已有 canonical 数据  
  中间产物   DraftModel（内存）   paper.yaml（可版本管理）  
  产物   直接入库（.epak 自动生成）   `.epak` 文件 / 静态 HTML  
  联网   扫描/翻译时需要   扫描时需要  
  适合   一次一份、边扫边改   批量、可复跑、可审查  

两者共用同一份 `epak` 格式与同一套字段语义，所以 App 里生成的包可以拿到电脑上 `exam inspect`，framework 生成的包可以直接导入 App。

## 9. 目录/职责边界

  谁   不该做什么  
 --- --- 
  canonical 数据 / 草稿   不写渲染细节、不写 CSS/颜色  
  framework   不碰 Android/UI；只做校验、编译、扫描、打包  
  Android App   不解析原始 HTML；只消费 `.epak`；编辑只改原文/讲解/翻译  

## 10. 阅读页交互（本轮细节）

  交互   实现   数据/表  
 --- --- --- 
  点任意英文单词 → 底部悬浮小岛   `TappableSegmentsText`：`TextLayoutResult.getOffsetForPosition` 把点击坐标换成字符偏移，向两侧扩成单词；`WordIsland` 显示单词/音标/词性/释义/来源   先查 `dict_entries`（精确匹配）；未收录则调 `TranslateService`（阿里云 MT 或千问）；可一键写 `wordbook`  
  译文默认收起   `SectionScreen.showZh` 初始 `false`；顶栏「展开译文/收起译文」总开关；每段独立「展开译文」   无持久化（每次进入默认收起）  
  收藏本（跨试卷）   首页星标进入 `CollectionScreen`；搜索框过滤；每条显示「来自：试卷名」   `StudyDao.observeCollectionWords / observeCollectionSentences`（`wordbook`/`sentences` JOIN `papers`）  
  出卷入口极简   首页只有一个圆形「＋」：扫描出卷 / 导入试卷包   无  
  AI 配置极简   设置只填 1 个「千问 API Key」，识别与翻译共用；模型与地址硬编码在 `AiConfig`   `settings` 表  

> 每份试卷自己的「生词本 / 收藏句」入口保留，收藏本是额外的一层跨卷汇总，不替代它们。
## 11. 后台扫描与草稿持久化

```
点「开始识别」
   └─ ScanService（前台服务，foregroundServiceType=dataSync）
        ├─ 前台通知：进度 x/y（国产 ROM 常渲染为胶囊/灵动岛）
        ├─ VisionClient 逐张识别 → 每张进度写 ScanJobBus
        ├─ 成功：DraftMapper.fromCanonical → 写 drafts 表 → 通知「扫描完成」
        │        ScanJobBus.Success → AppNav 顶部横幅（任何页面可见）
        └─ 失败：通知 + 横幅显示原因，可重试
```

| 组件 | 职责 |
|---|---|
| `ScanService` | 后台执行扫描；持有应用级协程；结束时 `stopForeground(DETACH)` 保留完成通知 |
| `ScanJobBus` | 进程内状态总线（Idle/Running/Success/Failed），服务写、横幅读 |
| `ScanBanner` | 在 `AppNav` 的 `Box` 顶层覆盖，任何路由都能看到；完成时点「查看」进编辑器 |
| `drafts` 表 | 单行草稿（`id='current'`，JSON = `DraftSnapshot{compiled, mindmaps, sourceLabel}`）；编辑器每 10 秒覆盖写 |
| `DraftRepository` | 草稿读写清空；`hasDraft` 供首页显示「继续未保存的草稿」 |
| 数据库迁移 | v1→v2 只 `CREATE TABLE drafts`，**不清库** |

注意事项：
- 前台服务在系统内存紧张时仍可能被回收；草稿表能保住「已识别/已编辑」的内容，但正在跑的那次扫描会中断（横幅会停在 Running，需重新开始）。
- 通知权限（Android 13+）只影响提醒能否显示，不影响扫描本身。
- 真正的「灵动岛」是厂商私有 API / Android 16 Live Updates，通用 App 无法直接调用；本方案用「前台服务常驻通知 + 应用内横幅」达到同等可见性。

## 12. 两段段出卷：扫描卷 → 解析卷

```
段段①  千问 VL（qwen3-vl-plus，自动探测）
        只转写：篇目 / 原文 / 题干 / 选项 / 题号 / 重点词
        禁止：答题、解析、翻译（normalize() 里强制清空 answer/analysis）
        ↓  VisionClient.normalize -> KExam -> DraftMapper.fromCanonical
        草稿 stage = "scanned"  → 可「保存为扫描卷」

段段②  老师确认答案（编辑器：选择题点 FilterChip、填空题填词）
        ↓  点「生成解析并保存解析卷」
        DeepSeek（deepseek-chat）逐篇调用 DeepSeekAnalyzer.analyzeSection：
          输入 {sectionTitle, passage, paragraphCount, questions[no/stem/options/teacherAnswer]}
          输出 {parasZh[], questions[{no, aiAnswer, analysis{solutions[], groups[]}}]}
        App 比对 aiAnswer 与老师答案 → 不一致的进 AnswerConflict 清单，让老师逐条裁决
        ↓  DraftModel.stage = "analyzed" → 「保存为解析卷」
```

| 数据字段 | 位置 | 说明 |
|---|---|---|
| `stage` | `CExam`（epak）/ `papers` 表 | `scanned` / `analyzed`；试卷库/目录页都会显示标签 |
| `answer` | `DraftQuestion.answer`（可编辑） | 老师可自己拟定；AI 答案只用于核对与「采用」 |
| DB 版本 | v3 | v1→v2 加 `drafts` 表；v2→v3 给 `papers` 加 `stage`（`DEFAULT 'analyzed'`），**都不清库** |

**职责边界**：视觉模型不写中文长文（省 token 且质量差），文本模型不看图（便宜且解析更细）。
`framework/src/vision.mjs` 仍走旧的「一次出全套」模式，属于电脑端场景，与 App 的两段段互不影响；App 产出的 `.epak` 里 `stage` 字段 framework 会忽略。