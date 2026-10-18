# MCP 接入说明（电脑端 AI 工作流，可选）

> **手机端不需要这个**：App 里已经内置「扫描 → 编辑 → 自动生成 .epak」，老师在手机上就能出卷。
> 这里讲的是**电脑上批量/自动化**场景：让 AI 客户端直接调工具，或在 CI 里跑。

## 1. 服务形态

`exam mcp` 启动一个 **stdio MCP 服务**（newline-delimited JSON-RPC 2.0，协议版本 `2024-11-05`），零第三方 MCP 依赖，手写实现，不会被 SDK 版本变化搞坏。

```jsonc
// 客户端配置（Claude Desktop / 任意 MCP 客户端）
{
  "mcpServers": {
    "exam-framework": {
      "command": "node",
      "args": ["D:\\dsh_work\\EA\\framework\\bin\\exam.mjs", "mcp"],
      "env": {
        "EXAM_VISION_API_KEY": "sk-...",
        "EXAM_VISION_BASE_URL": "https://api.openai.com/v1",
        "EXAM_VISION_MODEL": "gpt-4o-mini"
      }
    }
  }
}
```

也可以把 key 写进 `framework/framework.config.json`（照 `config.example.json` 复制）。

## 2. 工具清单

| 工具 | 输入 | 作用 |
|---|---|---|
| `exam_get_schema` | – | 返回完整 JSON Schema。**AI 产出数据前必须先读** |
| `exam_scan_paper` | `images[]`、`outPath?`、`id?`、`title?`、`full?`、`model?`、`baseUrl?`、`apiKey?` | 图片 → vision → 结构化试卷 YAML，并自动校验；返回错误/警告与下一步建议 |
| `exam_validate` | `path` | 校验结构与完整性，返回人话错误 + 题量统计 |
| `exam_build` | `path`、`target: android\|web`、`outDir?`、`installAssets?` | 编译成 `.epak`（可选自动投放进 App assets）或 web 预览 |
| `exam_inspect_pack` | `path` | 包体检：题数、空位数、解析完整度、导图数、manifest 一致性 |
| `exam_extract_legacy_html` | `htmlPath`、`outDir?` | 从旧版单页 HTML 抽数据（历史资产迁移） |
| `exam_export_yaml` | `path`、`outPath?` | canonical JSON → YAML，便于人工修 |
| `exam_list_papers` | `dir?` | 列出内容目录里已有试卷 |

`exam_scan_paper` 返回示例：

```json
{
  "ok": false,
  "outPath": "D:\\dsh_work\\EA\\content\\scan-2026-10-17\\paper.yaml",
  "stats": { "sections": 4, "questions": 21, "choiceQuestions": 21, "blankQuestions": 0, "withAnalysis": 8 },
  "errors": ["sections[2](C): 原文有这些空位但缺少对应题目：43, 44"],
  "warnings": ["questions[7]: 缺少解析（solutions 为空）"],
  "next": "请按 errors 修订 YAML 后 exam_validate 复核"
}
```

**扫描结果永远先过校验器**：`ok:false` 不阻塞写文件（方便修），但 `exam_build` 会拒绝打包半成品。

## 3. 典型 AI 工作流

```
用户：把 D:\试卷\11月月考\*.png 做成 App 能用的试卷包

AI:
 1. exam_get_schema                          # 先读契约（必须）
 2. exam_scan_paper  images=[p1.png, p2.png, p3.png], id="school-2026-11", title="某校 11 月月考"
 3. 若 errors 非空 → 自己按 errors 改 YAML（exam_export_yaml / 直接编辑）→ exam_validate 复核
 4. exam_build       path=paper.yaml, target=android     # 产出 .epak，自动投放 app assets
 5. exam_build       path=paper.yaml, target=web         # 给用户一个 index.html 先验收
 6. exam_inspect_pack path=...epak                        # 自检题量/解析完整度
```

对应的人肉等价命令：

```powershell
cd D:\dsh_work\EA\framework
node bin/exam.mjs scan ..\content\school-2026-11\*.png --out ..\content\school-2026-11\paper.yaml --id school-2026-11 --title "某校 11 月月考"
node bin/exam.mjs validate ..\content\school-2026-11\paper.yaml
node bin/exam.mjs build ..\content\school-2026-11\paper.yaml --target android
```

## 4. vision 配置

OpenAI 兼容协议即可（官方、Azure 网关、通义/DeepSeek 的中转、本地 vLLM 都行）：

| 环境变量 | 说明 | 默认 |
|---|---|---|
| `EXAM_VISION_API_KEY` | 必填 | – |
| `EXAM_VISION_BASE_URL` | 兼容 `/chat/completions` 的 base | `https://api.openai.com/v1` |
| `EXAM_VISION_MODEL` | 视觉模型名 | `gpt-4o-mini` |

实现要点（`framework/src/vision.mjs`）：

- system prompt 里**内联完整 JSON Schema**，要求「只输出 JSON」；
- 每张图片单独请求，多张结果按 `section.id` 合并、题目按 `no` 去重排序；
- 输出容错：剥 ```` ```json ```` 围栏、取第一个 `{` 到最后一个 `}`；
- 超时 180s，失败抛带响应片段的错误。

**准确率预期**：清晰印刷体、单栏排版最好；表格、双栏、手写批注容易漏。所以流程里把 `exam_validate` 当闸门，扫完先看 web 预览再导入 App。

## 5. 自测

```powershell
cd D:\dsh_work\EA\framework
node test/smoke.mjs
```

会拉起 `exam mcp` 子进程，走一遍 `initialize → tools/list → tools/call(exam_get_schema / exam_validate / exam_build / exam_inspect_pack)`，并检查产物。
