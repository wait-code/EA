# 迁移记录：旧版单页 HTML → 通用数据包

源文件：`德阳五中10月自主测验英语 全卷互动精讲.html`（3,520,923 字节 / 2,047 行，sha256 `b4d134f8…`）

## 1. 原文件结构（实测）

| 块 | 位置 | 规模 | 处置 |
|---|---|---|---|
| `window.__PAPER__` | 第 610 行 | 7 篇 / 45 题 / 28 段 / 187 重点词 / 985 词典 | 全量迁移 |
| `window.__QUIZ__` | 第 612 行 | 3 部分 / 60 题 + 20 空 | 全量迁移 |
| `window.__MM__` | 第 613 行 | 7 个 SVG 思维导图（约 60 KB） | 迁移为 `mindmaps/*.svg` |
| `window.__AUDIO__` | 第 611 行 | 7 段 MP3 base64，3.07 MB | **丢弃**（按需求删朗读功能） |
| 批注画板 | 第 409 / 475 / 605 / 1538 行 | canvas 圈画层 | **丢弃** |
| docx 导出 / 打印 | 第 1098–1250 行 + print CSS | 约 450 行 | **丢弃** |
| localStorage 存档 | 全文 2 处 | 单一 `ST` JSON | 换成 Room 正式表 |

抽取脚本：`tools/extract-legacy.mjs`（也可用 `exam extract-legacy-html <html>` 或 MCP 工具 `exam_extract_legacy_html`）。

## 2. 零丢失验收

抽取报告 `content/deyang-2025-10/extract-report.json`：

```
sections 7 · questions 45 · paragraphs 28 · sectionVocab 187
migrations 21 · outlineNodes 90 · dictEntries 985
quizParts 3 · quizItems 60 · quizSegmentBlanks 20 · mindmaps 7
questionsWithOptions 35 · questionsWithAnalysis 45
droppedAudioClips 7
legacyExtrasFound: (none)      ← 关键：旧数据里没有「新结构装不下的字段」
```

`legacyExtrasFound` 为空，说明逐字段映射没有遗漏。字段对照：

| 旧字段 | 新字段 | 说明 |
|---|---|---|
| `section.tab / ptitle / chip / topic` | `tabLabel / passageTitle / chip / topic` | 改名，语义不变 |
| `section.struct[] {k,t}` | `outline[] {level,text}` | `l1/l2/l3` 原样保留 |
| `section.migrate[] {t,d}` | `migrations[] {title,detail}` | |
| `section.vocab[] {w,ph,pos,zh}` | `vocab[] {word,phonetic,partOfSpeech,zh}` | |
| `question.ans_letter/ans_text/ans_zh/hint` | `answer.letter/text/zh/hint` | 收进 `answer` 对象 |
| `question.ana {solLb, sol[], groups[]}` | `analysis {solutionLabel, solutions[], groups[]}` | 解析的分组结构保留，不压成字符串 |
| `quiz.parts[].segs / ans` | `segments / answers` | 见下方第 4 节 |
| `dict {word:[ph,pos,zh]}` | `dict[] {word,phonetic,partOfSpeech,zh}` | map → 数组 |

## 3. 空位标记的三种历史写法（真实存在的坑）

同一份卷子里出现了三种：

```
{{41}}                    完形/七选五：只有编号
{{59|}}                   语法填空：编号 + 空提示
{{ {{56|}} |it}}          语法填空：外层再包一层（旧渲染器产物）
```

抽取时统一归一成 `{{题号|提示}}`，编译时转成 `{t:"blank", no:"56", hint:"it"}`。校验器会做**对账**：

- 原文里出现的空位编号，必须有对应题目；
- 题目里标注的空位，最好在原文里找得到（找不到给警告）。

实测：完形 15 空、语法填空 10 空、七选五 5 空 = 30 空，与 45 题里的 30 道挖空题完全对应。

## 4. 词卷第三部分：答案词写在句末的坑

数据结构是 `segments[i]` + `answers[i]`，而**每段结尾就是答案词本身**：

```
segments[0] = "Last spring my friend and I decided to change our unhealthy lifestyle"
answers[0]  = "lifestyle"
```

旧 HTML 的渲染是「先打印整段（含答案词），再跟一个输入框」——学生看到的原文里答案已经印出来了。打印版同理（`t += ' __________ '`）。

新实现里做了**教学上正确的归一**：编译时把段尾答案词摘掉，换成真正的空位。

```
原文 → "Last spring my friend and I decided to change our unhealthy ［1］"
```

对应代码：`framework/src/compile.mjs` 的 `compileQuizPassage()`；校验器也会提示「该段结尾不是答案词」以防数据写错。

## 5. 旧版功能 → 新版页面/表

| 旧功能 | 新版位置 |
|---|---|
| 左侧篇目 chip 切换 | 试卷目录页的篇目卡片 |
| 原文 + 中文翻译切换 | 篇目页「原文」页签 + 顶栏「译文开/关」 |
| 逐题作答（选择 / 填空） | 篇目页「题目」页签（`study_state` 表持久化） |
| 显示答案 / 逐项解析 / 干扰项排除 | 题目卡片内展开（`analysis.solutions/groups`） |
| 点词查释义 | 原文页「查词」输入框 + 重点词 chip（`dict_entries`） |
| 生词本 | 生词本页（`wordbook`） |
| 收藏句 | 收藏句页（`sentences`，入口在题目卡片） |
| 语篇结构导图 | 篇目页「拓展」页签（`outline`） |
| 写作迁移 | 篇目页「拓展」页签（`migrations`） |
| 思维导图 SVG | 篇目页「拓展」页签（Coil + SVG 解码） |
| 词汇检测卷 + 自动判分 | 词汇检测页（`quiz_parts` / `quiz_items` / `quiz_answers`） |
| 字号 / 皮肤切换 | 设置页（字号倍率；主题固定学院蓝） |
| 双栏对照（PC） | 宽屏 ≥900dp 自动双栏；窄屏「对照原文」抽屉 |
| 批注 / 朗读 / Word / 打印 | **已移除** |

## 6. 体积变化

| | 旧 | 新 |
|---|---|---|
| 内容载体 | 3.52 MB 单 HTML（含 3.07 MB base64 音频） | 338 KB `.epak`（无音频） |
| 运行时 | 浏览器 + localStorage | 原生 App + SQLite |

## 7. 如果要再迁一份旧 HTML

```powershell
cd D:\dsh_work\EA\framework
node bin/exam.mjs extract-legacy-html D:\path\to\other.html --out ..\content\other-exam
node bin/exam.mjs validate ..\content\other-exam\paper.json
node bin/exam.mjs build    ..\content\other-exam\paper.json --target android
```

前提：旧 HTML 里同样有 `window.__PAPER__` 之类的内嵌 JSON（`tools/extract-legacy.mjs` 按这个特征识别）。字段对不上时先跑一遍，看 `extract-report.json` 的 `legacyExtrasFound` 与 `legacyKeySets`，再补映射。
