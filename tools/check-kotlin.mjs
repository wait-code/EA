/**
 * Kotlin 源码静态检查（没有编译器时的最低保障）：
 *   1. 花括号/圆括号平衡
 *   2. 重复的顶层函数名（同一 package 下）
 *   3. 关键符号出现次数（防止重构后残留旧调用）
 *   4. 可疑残留：旧版批注/朗读/导出/打印代码
 *
 *   node tools/check-kotlin.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..');
const root = path.join(repo, 'android', 'app', 'src', 'main', 'java');

function walk(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc;
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) walk(p, acc);
    else if (ent.name.endsWith('.kt')) acc.push(p);
  }
  return acc;
}

/** 去掉字符串与注释，避免括号统计被内容干扰
 *  注意顺序：必须先剥字符串，再剥注释——否则 "https://..." 里的 // 会被当成注释起点。 */
function stripCode(src) {
  return (
    src
      .replace(/"""[\s\S]*?"""/g, '""')
      // 字符字面量要先于双引号字符串处理：'"' 这类字符里含双引号
      .replace(/'(?:\\.|[^'\\\n])'/g, "''")
      .replace(/"(?:[^"\\\n]|\\.)*"/g, '""')
      .replace(/\/\*[\s\S]*?\*\//g, ' ')
      .replace(/\/\/[^\n]*/g, ' ')
  );
}

let problems = 0;
const files = walk(root);
console.log(`Kotlin 文件：${files.length} 个\n`);

console.log('== 1. 括号平衡 ==');
for (const file of files) {
  const code = stripCode(fs.readFileSync(file, 'utf8'));
  let curly = 0;
  let paren = 0;
  let minCurly = 0;
  for (const ch of code) {
    if (ch === '{') curly += 1;
    else if (ch === '}') {
      curly -= 1;
      minCurly = Math.min(minCurly, curly);
    } else if (ch === '(') paren += 1;
    else if (ch === ')') paren -= 1;
  }
  if (curly !== 0 || paren !== 0 || minCurly < 0) {
    problems += 1;
    console.log(`  ✗ ${path.relative(repo, file)}  curly=${curly} paren=${paren}`);
    // 打印括号累计走偏的位置，便于定位
    let running = 0;
    code.split(/\r?\n/).forEach((line, index) => {
      for (const ch of line) {
        if (ch === '(') running += 1;
        else if (ch === ')') running -= 1;
      }
      if (running < 0) {
        console.log(`      第 ${index + 1} 行起圆括号累计为 ${running}：${line.trim().slice(0, 90)}`);
        running = 0;
      }
    });
  }
}
if (problems === 0) console.log('  ✓ 全部平衡');

console.log('\n== 2. 顶层函数重名（同包） ==');
const byPackage = new Map();
for (const file of files) {
  const src = fs.readFileSync(file, 'utf8');
  const pkg = (src.match(/^package\s+([\w.]+)/m) || [])[1] || '(default)';
  const names = [...src.matchAll(/^(?:private\s+|internal\s+)?fun\s+([A-Za-z_][\w]*)\s*\(/gm)]
    .map((m) => m[1])
    .filter((n) => !['Composable'].includes(n));
  if (!byPackage.has(pkg)) byPackage.set(pkg, new Map());
  const map = byPackage.get(pkg);
  for (const name of names) {
    if (map.has(name)) {
      problems += 1;
      console.log(`  ✗ ${pkg}.${name} 在 ${path.basename(map.get(name))} 与 ${path.basename(file)} 重复`);
    } else {
      map.set(name, file);
    }
  }
}
if (problems === 0) console.log('  ✓ 无重名');

console.log('\n== 3. 关键符号出现次数 ==');
const all = files.map((f) => fs.readFileSync(f, 'utf8')).join('\n');
const symbols = [
  'DraftModel',
  'DraftMapper',
  'DraftValidator',
  'DraftStore',
  'EpakWriter',
  'VisionClient',
  'TranslateService',
  'importCompiled',
  'loadCompiled',
  'CManifestFiles',
  'optionTexts',
  'Inline.parse',
  'Inline.toRaw'
];
for (const symbol of symbols) {
  const count = all.split(symbol).length - 1;
  console.log(`  ${count.toString().padStart(3)}  ${symbol}${count === 0 ? '   ← 可能未接线' : ''}`);
}

console.log('\n== 4. 已删功能残留 ==');
const banned = [
  ['批注画板', /btnDraw|ExitDraw|drawCanvas|annotationLayer/i],
  ['朗读音频', /audioBar|MediaPlayer|speechSynthesis|__AUDIO__/i],
  ['Word 导出', /docx|wordprocessingml/i],
  ['打印', /window\.print|print-sec|print-quiz/i],
  ['离线字样', /离线可用|完全离线/],
  ['关于页', /Text\("关于"/],
  ['阿里云翻译残留', /TranslateGeneral|aliyunAccessKey|HmacSHA1|mt\.cn-hangzhou/]
];
let bannedHits = 0;
const sourceFiles = files.map((f) => [path.relative(repo, f), fs.readFileSync(f, 'utf8')]);
for (const [label, re] of banned) {
  const hits = sourceFiles.filter(([, src]) => re.test(src)).map(([f]) => f);
  if (hits.length === 0) console.log(`  ✓ ${label}：无残留`);
  else {
    bannedHits += 1;
    problems += 1;
    console.log(`  ✗ ${label}：${hits.join(', ')}`);
  }
}

console.log('\n== 5. 新增能力接线核查 ==');
const wiring = [
  ['扫描入口（首页 ＋ 按钮）', /Routes\.scan\(\)/],
  ['编辑器入口', /Routes\.editor\(/],
  ['收藏本入口/页面', /Routes\.collection\(\)/],
  ['点词小岛', /TappableSegmentsText/],
  ['译文默认收起', /var showZh by remember\(sectionId\) \{ mutableStateOf\(false\) \}/],
  ['千问视觉模型候选（qwen3-vl-plus 优先）', /VISION_CANDIDATES[\s\S]*qwen3-vl-plus/],
  ['国内/国际站自动切换', /QWEN_BASE_URL_INTL/],
  ['视觉模型 404 自动降级', /markFailed\(/],
  ['翻译模型候选降级', /TEXT_CANDIDATES/],
  ['单一主色（学院蓝）', /AccentBlue = Color\(0xFF1D5FC0\)/],
  ['思维导图可放大', /ZoomableImage|放大查看/],
  ['设置：千问 Key', /setQwenKey\(/],
  ['设置：DeepSeek Key', /setDeepSeekKey\(/],
  ['两阶段：扫描卷/解析卷', /stage == "analyzed"/],
  ['DeepSeek 解析客户端', /DeepSeekAnalyzer/],
  ['答案可编辑', /question\.answer = question\.answer\.copy/],
  ['AI 答案不符提示', /AnswerConflict/],
  ['扫描只转写（不答题）', /不答题、不写解析、不翻译|不要给 answer/],
  ['DeepSeek 自动补答案', /autoFilled/],
  ['扫描胶囊可拖动悬浮', /detectDragGestures/],
  ['点词小岛平滑过渡', /AnimatedContent/],
  ['开源致谢页（含作者/DSH）', /CreditsScreen/],
  ['作者链接 wait-code', /github\.com\/wait-code/],
  ['响应式限宽列表', /AdaptiveLazyColumn/],
  ['Linus 名言在正文（非顶栏）', /LINUS_QUOTE/],
  ['单词查词带缓存与最短等待', /wordCache/],
  ['后台扫描前台服务', /ScanService/],
  ['扫描状态横幅（任意页面可见）', /ScanBanner/],
  ['草稿每 10 秒自动保存', /delay\(10_000\)/],
  ['草稿落库（drafts 表 + 迁移）', /draftRepository/],
  ['前台服务 / 通知权限声明', /FOREGROUND_SERVICE_DATA_SYNC[\s\S]*POST_NOTIFICATIONS/],
  ['网络权限', /android\.permission\.INTERNET/]
];
const projectFiles = [
  ...files.map((f) => [path.relative(repo, f), fs.readFileSync(f, 'utf8')]),
  [path.join('android', 'app', 'src', 'main', 'AndroidManifest.xml'),
    fs.readFileSync(path.join(repo, 'android', 'app', 'src', 'main', 'AndroidManifest.xml'), 'utf8')]
];
for (const [label, re] of wiring) {
  const hit = projectFiles.some(([, src]) => re.test(src));
  if (hit) console.log(`  ✓ ${label}`);
  else {
    problems += 1;
    console.log(`  ✗ ${label} 没找到`);
  }
}

console.log(problems === 0 ? '\n全部通过 ✓' : `\n发现 ${problems} 个问题 ✗`);
process.exit(problems === 0 ? 0 : 1);
