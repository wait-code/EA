# 编译与测试指南

> 你的 SDK 路径我不猜，下面统一用 `$env:ANDROID_HOME`；也可以显式传 ` Sdk`。

## 一、开工前的 30 秒自检（不需要 Android SDK）

```powershell
cd D:\dsh_work\EA
node tools/check-all.mjs      # 内容→编译→包体检→工程与 Kotlin 静态检查，最后一行「全部通过 ✓」
node tools/check-kotlin.mjs   # 只看 Kotlin：括号平衡 / 重名 / 关键符号接线 / 已删功能残留
```

顺带跑一下 MCP 服务（AI 接入层，电脑端可选）：

```powershell
cd D:\dsh_work\EA\framework
node test/smoke.mjs      # 预期 19 项全 ✓，最后打印「全部通过」
```

## 二、编译 APK

### 方式 A：Android Studio（推荐）

1. `File → Open` → 选 **`D:\dsh_work\EA\android`**
2. 等 Sync（wrapper 已内置，发行包走腾讯云镜像；缺 SDK 组件会提示安装：**Platform 35** + **Build Tools 35.0.0**）
3. Run ▶ 直接装机；或 `Build → Build APK(s)`
4. 产物：`android\app\build\outputs\apk\debug\app debug.apk`

### 方式 B：命令行（你 SDK 下完后）

```powershell
# 先确认 java 17
java  version

cd D:\dsh_work\EA
.\tools\build-android.ps1  Sdk "C:\Users\34054\AppData\Local\Android\Sdk"
```

脚本会做 4 件事：写 `local.properties` → 重新编译内容包（`content/` → `assets/seed.epak`）→ `gradlew assembleDebug` → 打印 APK 路径与 `adb install` 命令。

不想用脚本：

```powershell
$env:ANDROID_HOME = "C:\Users\34054\AppData\Local\Android\Sdk"
cd D:\dsh_work\EA\android
.\gradlew.bat assembleDebug
```

### 装到手机

```powershell
adb install  r D:\dsh_work\EA\android\app\build\outputs\apk\debug\app debug.apk
```

## 三、首次启动预期

1. App 名「试卷精讲」，首屏是**试卷库**，里面已经有 1 份：`德阳五中10月学生自主测验`（来自 `assets/seed.epak`）。
2. 卡片上应有：**篇目 7 / 题目 45 / 查词 985 / 词卷 有**。
3. 若这里是 0 份：说明种子包没进 APK，检查 `android/app/src/main/assets/seed.epak` 是否存在（339 KB）。
4. 进「设置」应能看到两栏：**千问 API Key**（扫描）与 **DeepSeek API Key**（解析）。

## 三之二、App 内出卷与编辑（新增功能，重点验收）

### 1. 配置 Key

  - [ ] 设置页有两个 Key 输入框：「千问 API Key」「DeepSeek API Key」；没有 Base URL、模型名、地域这些技术字段
  - [ ] 设置页**没有「关于」**卡片
  - [ ] 粘贴千问 Key →「保存」→ 状态变「已配置」
  - [ ] 点「测试翻译」→ 提示 `silent → 沉默的`（或类似）
  - [ ] 粘贴 DeepSeek Key →「保存」→ 状态变「已配置」

### 2. 扫描出卷

  - [ ] 首页右下角只有一个圆形 **「＋」** 按钮（不是长条按钮）
  - [ ] 点「＋」→ 弹窗只有两项：**扫描试卷出卷** / **导入试卷包**
  - [ ] 首页标题下方只显示「共 N 份试卷」
  - [ ] 点「扫描试卷出卷」→ 顶部显示「识别模型：自动适配（首选 qwen3-vl-plus）」；未配置 Key 时提示去设置
  - [ ] 「选择图片」可以多选；选中后列出「第 N 张：文件名」，可单张删除/清空
  - [ ] 「开始识别（后台进行）」→ 有进度提示；识别成功自动跳到编辑器；失败显示错误卡片与常见原因

### 3. 编辑器（两阶段）

  - [ ] 顶部显示「扫描卷 · 扫描=千问VL 解析=DeepSeek」
  - [ ] 试卷信息卡可改标题、全名、卷面信息、id；显示「共 N 篇 / M 题　答案已填 x 题」
  - [ ] 每篇卡片默认收起，点箭头展开；展开后：
    - [ ] 「英文原文」文本框可编辑，提示里写明 `{{题号}}` 写法
    - [ ] 「中文翻译」文本框可编辑
    - [ ] 翻译右侧有「译」按钮：点它自动把英文译成中文填进去
    - [ ] 重点词汇行：释义可改，每行有「译」按钮
    - [ ] **答案可编辑**：选择题显示 A/B/C/D 按钮（点选/取消），填空题显示「标准答案（自己拟定）」输入框
    - [ ] 题干、讲解条目可编辑
    - [ ] 挖空题卡片顶部有「空位与题号一致（N 个空）」或指出问题
  - [ ] 故意把一个段落的 `{{41}}` 改成 `{{99}}` → 卡片提示「原文有 99 但没有对应题目」
  - [ ] 底部有**两个按钮**：「保存为扫描卷」「生成解析并保存解析卷」
  - [ ] 保存 → 自动跳到该试卷目录页（`.epak` 已自动生成并入库）

### 4. 编辑已有试卷

  - [ ] 试卷目录页右上角有「编辑内容」
  - [ ] 点进去能看到已有内容（原文/翻译/讲解都在，思维导图保留）
  - [ ] 改一段翻译 → 保存 → 回目录进篇目，原文页显示修改后的译文
  - [ ] 注意：同 id 覆盖会清空该卷的作答与生词本（预期行为）

### 5. 点词小岛与译文开关（阅读页）

  - [ ] 进任意阅读篇目：**译文默认不展开**（没有中文块）
  - [ ] 顶栏按钮显示「展开译文」；点一下全篇译文出现，再点变「收起译文」
  - [ ] 每段下方有「展开译文」小按钮，可单独展开这一段（不影响别的段）
  - [ ] 点正文里任意英文单词 → 底部浮出**小岛**：大字单词 + 音标 + 词性 + 中文
  - [ ] 小岛里有「加入生词本」，点完变「已加入生词本」；点「关闭」小岛消失
  - [ ] 点一个生僻词 → 小岛显示来源「机器翻译」；翻译服务都没配时显示「词典未收录：…」
  - [ ] 题干与解析里的英文单词也能点出小岛

### 6. 收藏本（跨试卷）

  - [ ] 首页右上角有星标图标 → 进入「收藏本」
  - [ ] 顶部显示「生词 N · 句子 M」；下面有搜索框
  - [ ] 生词页签：每条显示单词/音标/词性/释义 + **来自：某某试卷**
  - [ ] 句子页签：每条显示句子 + **来自：某某试卷**
  - [ ] 搜索单词或试卷名，列表能被过滤
  - [ ] 删除按钮能移除条目
  - [ ] 试卷目录页原有的「生词本 / 收藏句」入口仍在，且只显示本卷内容

### 7. 断网行为

  - [ ] 关掉网络：试卷浏览、答题、显示答案、生词本、收藏本、词卷判分 **全部正常**
  - [ ] 「译」按钮、点词小岛（词典未收录时）、「开始识别」会报网络错误（预期）

## 四、手动验收清单（照着点，逐项对预期）

### 1. 试卷目录

  - [ ] 点开试卷 → 顶部信息卡显示「满分 150 分 · 时间 120 分钟」
  - [ ] 下面 7 张篇目卡片：`A · 21–23`、`B · 24–27`、`C · 28–31`、`D · 32–35`、`S`（七选五）、`Z`（完形）、`G`（语法填空）
  - [ ] 三个入口 chip：词汇检测 / 生词本 / 收藏句

### 2. 阅读 A 篇（点 A 卡片）

  - [ ] 默认「原文」页签：英文 5 段，带小标题（Carla Sonheim's Kid's Week 等）
  - [ ] 顶栏「译文开/关」能切换，关掉后中文消失
  - [ ] 「查词」输入 `affordable` → 出现 `/əˈfɔːdəbl/ adj. 负担得起的；价格合理的` + 「＋生词本」
  - [ ] 底部「本篇重点词汇」chips 可点，点完提示已加入生词本
  - [ ] 切到「题目」页签：第 21/22/23 题，每题 4 个选项
  - [ ] 点选项 → 选中态变色；再点一次取消
  - [ ] 点「显示答案」→ 出现红色正确答案 + 「原文依据 / 整句翻译 / 正确选项理由 / 干扰项排除」逐条解析
  - [ ] 解析里正确选项是红色、考点短语是橙色底
  - [ ] 「收藏此句」→ 提示已收藏
  - [ ] 切到「拓展」：有语篇结构（l1/l2/l3 缩进）、写作迁移、**思维导图 SVG 能正常显示**
  - [ ] 点思维导图或「放大查看」→ 全屏黑底打开，可双指缩放/拖动，点「关闭」返回

### 3. 完形填空 Z

  - [ ] 「原文」里能看到 `〔41〕`…`〔55〕` 编号空位（红字）
  - [ ] 「题目」里 15 题，选项 4 个，答案可显示

### 4. 语法填空 G

  - [ ] 「原文」空位带所给词提示（如 `〔56〕`，提示 `it`）
  - [ ] 「题目」里没有选项，是输入框 + 答案文本
  - [ ] 显示答案后出现「答案：Its（它的）」「所给词：it」

### 5. 七选五 S

  - [ ] 每道题的选项是 A–G 共 7 句（不是 4 个）

### 6. 词汇检测（目录页 → 词汇检测）

  - [ ] 顶部 3 个部分页签：`一 据音标与词义写单词` / `二 原文句子选词填空` / `三 短文选词填空`
  - [ ] 第一部分 40 题，每题给音标 + 词性 + 中文，输入框填词
  - [ ] 第二部分 20 题，显示选词框（40 个词）+ 挖空句子
  - [ ] 第三部分：**短文里是空格子**（不是把答案词印在原文里），共 20 空
  - [ ] 填几题后点「提交评分」→ 弹窗给得分（每题 1 分）；答对的题显示 ✓、错的 ✗
  - [ ] 「显示答案」能看到每个正确答案
  - [ ] 「重做本部分」清空本部分

### 7. 生词本 / 收藏句 / 持久化

  - [ ] 生词本里能看到刚才加的词；点垃圾桶可移除
  - [ ] 收藏句里能看到刚才收藏的句子
  - [ ] **杀掉 App 再打开**：作答选择、填空内容、生词本、收藏句都还在（= SQLite 真的落盘了）

### 8. 设置

  - [ ] 拖字号滑块 → 返回阅读页，字号变了
  - [ ] 设置页只有「字号」滑块，**没有配色选择**；全 App 主色固定学院蓝（#1D5FC0）
  - [ ] 设置页没有「默认显示中文译文」开关（译文默认收起，在阅读页用按钮展开）
  - [ ] 「导入试卷包」能选到 `.epak` 并导入（可以拿 `build\deyang-2025-10-english.epak` 试：会提示导入成功并覆盖同 id）
  - [ ] 「删除这份试卷」→ 回列表，试卷消失

### 9. 已删功能核查（关键验收点）

在 App 里翻一遍，**应该完全找不到**：

  - [ ] 没有「批注 / 圈画 / 手写」任何按钮或画布
  - [ ] 没有音频播放条 / 倍速 / 朗读
  - [ ] 没有「导出 Word」
  - [ ] 没有「打印」

### 10. 数据库核对（可选，需要 adb）

```powershell
adb shell run as com.examreader ls  l databases
# 拉出来看表
adb exec out run as com.examreader cat databases/exam reader.db > "$env:TEMP\exam.db"
```

表应包含：`papers / sections / paragraphs / questions / section_vocab / dict_entries / quiz_parts / quiz_items / study_state / quiz_answers / wordbook / sentences / settings / import_log`。

## 五、构建期常见错误对照

| 报错 | 原因 | 处理 |
|---|---|---|
| `SDK location not found` | 没 `local.properties` 也没 `ANDROID_HOME` | 用 `tools/build android.ps1  Sdk ...`，它自动写 |
| `Could not resolve com.android.tools.build:gradle:8.7.3` | 访问不了 Google Maven | 挂代理；或在 `settings.gradle.kts` 的 `repositories` 最前面加国内镜像 |
| `Unsupported class file major version` | JDK 不是 17 | `JAVA_HOME` 指到 `jdk 17`；Studio 里 `Gradle JDK` 选 17 |
| Sync 卡在下载 Gradle | 网络问题 | wrapper 已指向腾讯云镜像；仍卡就手动改 `gradle/wrapper/gradle wrapper.properties` 的 `distributionUrl` |
| `Unresolved reference: Icons.Filled.XXX` | 用了 material icons extended 才有的图标 | 只用当前 core 图标集（Add/Delete/Settings/ArrowBack），或加 `material icons extended` 依赖 |
| `@Composable invocations can only happen from...` | 在非 Composable 里调用了 UI | 把该逻辑搬到 composable / 用 remember 包起来 |
| Room `Cannot find setter for field` | 实体字段名与构造参数不一致 | 检查 `Entities.kt` |
| 首次启动空库 | `assets/seed.epak` 缺失 | 重新跑 `node framework/bin/exam.mjs build ../content/deyang 2025 10/paper.json   target android` |
| 导入失败「包版本不兼容」 | epak 版本不是 1 | 用当前 framework 重新 `exam build` |

## 五之二、后台扫描与草稿自动保存（新增）

### 1. 后台扫描
- [ ] 选好图片点「开始识别（后台进行）」→ 首次会弹**通知权限**，同意/拒绝都行
- [ ] 通知栏出现常驻通知「正在扫描试卷（x/y）」，部分国产 ROM 会显示成胶囊/灵动岛样式
- [ ] **直接返回首页/切到别的 App/退到桌面**，几分钟后回来：扫描仍在进行或已完成
- [ ] App 任意页面顶部出现横幅：扫描中（带取消）/ 扫描完成（点「查看」进编辑器）/ 扫描失败（带原因）
- [ ] 扫描完成时，下拉通知栏能看到「扫描完成 · 点此打开编辑器」，点击打开 App
- [ ] 点横幅「忽略」后横幅消失，草稿仍在（首页出现「有未保存的草稿」）
- [ ] 扫描中返回首页 → 不崩溃；点横幅「取消」→ 通知消失、状态复位

### 2. 草稿自动保存
- [ ] 进编辑器改几段文字，等 10 秒 → 出现「草稿已自动保存 · HH:mm:ss」
- [ ] **彻底杀掉 App（从最近任务划掉）** → 重开 → 首页出现「有未保存的草稿」→ 点「继续」→ 刚才的修改还在
- [ ] 编辑器里点「保存并自动生成」成功后 → 回首页，**「有未保存的草稿」消失**（草稿已清空）
- [ ] 未保存直接返回 → 草稿保留，可再次从首页「继续」
## 五之三、两阶段出卷：扫描卷 → 解析卷

### 1. 扫描只出文字（千问 VL）
- [ ] 设置里能看到两个 Key 输入：**千问 API Key**（扫描）与 **DeepSeek API Key**（解析）
- [ ] 扫描完成后进编辑器，顶部显示「扫描卷 · 扫描=千问VL 解析=DeepSeek」
- [ ] 扫描结果的题目**没有答案、没有解析**，段落**没有中文译文**（正常）
- [ ] 阅读页点「显示答案」时，没有解析的题会提示「这道题还没有解析（扫描卷）」
- [ ] 直接点「保存为扫描卷」→ 列表里这张卷标注「扫描卷」，可以正常看原文/做题

### 2. 答案编辑（老师确认）
- [ ] 编辑器里每题有「选择正确答案」（选择题 = A/B/C/D 按钮；填空题 = 输入框）
- [ ] 点字母后题干行显示「答案：C」；再点一次取消
- [ ] 首页/编辑器显示「答案已填 x/y 题」

### 3. 生成解析（DeepSeek）
- [ ] **答案全部留空**直接点「生成解析并保存解析卷」→ 不拦你；完成后提示「DeepSeek 已自动补全 N 道题的答案」，题目答案被自动填上
- [ ] 老师填过答案、且与 AI 不一致 → 弹「AI 答案与你的答案不一致」清单，可逐条「保留我的 / 采用 AI 答案」
- [ ] 扫描中/扫描完成后，顶部的扫描胶囊是**小卡片**不是整条横幅；**按住可拖动**到任意位置，不遮挡页面
- [ ] 点任意单词：小岛标题先切成该词并显示「正在查词典 / 翻译…」，约 0.2–0.5 秒后内容淡入替换；快速连点只显示最后一次、不闪
- [ ] 设置页底部有「开源致谢」→ 进去能看到 DjangoDancer、DSH、Kotlin/Compose、Room、Coil、kotlinx.serialization、通义千问、DeepSeek；点「打开」能跳浏览器（作者链接 https://github.com/wait-code）
- [ ] 没填 DeepSeek Key 时点生成 → 提示去设置填 Key
- [ ] 填完答案点生成 → 显示进度「正在生成解析 x/y：篇名」
- [ ] 生成完成后：段落出现中文译文；每题出现「原文依据/整句翻译/正确选项理由/干扰项排除」
- [ ] 若存在 AI 答案 ≠ 你的答案 → 弹出「AI 答案与你的答案不一致（N 题）」清单，每题有「保留我的 / 采用 AI 答案」
- [ ] 点「采用 AI 答案」→ 该题答案变成 AI 的字母/文本
- [ ] 点「确认并保存解析卷」→ 列表里这张卷标注「解析卷」
- [ ] 进阅读页 → 点「显示答案」能看到完整讲评，且译文可展开

### 4. 阶段持久化
- [ ] 扫描卷保存后重开 App 仍是「扫描卷」
- [ ] 从扫描卷点目录页「编辑内容」→ 继续填答案/生成解析 → 保存后变「解析卷」（同 id 覆盖会重置该卷作答）
- [ ] 数据库升级（旧安装包升上来）不需要重装试卷：v2→v3 只加字段

## 六、如果编译报错，这样反馈最快

把这段贴给我：

```powershell
cd D:\dsh_work\EA\android
.\gradlew.bat assembleDebug 2>&1 | Tee Object  FilePath ..\build\build log.txt
```

然后贴 `build\build log.txt` 里**从 `> Task :app:compileDebugKotlin FAILED` 开始往下 60 行**（含第一条 `e: file:///...` 错误）。

运行期崩溃则贴：

```powershell
adb logcat  d | Select String  Pattern "AndroidRuntime|com.examreader" | Select Object  Last 40
```

## 七、给 AI 用的测试回路（想自动化出卷时）

```powershell
cd D:\dsh_work\EA\framework
$env:EXAM_VISION_API_KEY="sk ..."
node bin/exam.mjs scan ..\试卷图\*.png   out ..\content\auto\paper.yaml   title "自动扫描测试"
node bin/exam.mjs validate ..\content\auto\paper.yaml
node bin/exam.mjs build ..\content\auto\paper.yaml   target web      # 先看网页预览对不对
node bin/exam.mjs build ..\content\auto\paper.yaml   target android  # 再进 App
```
