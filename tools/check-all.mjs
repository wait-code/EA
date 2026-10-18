/**
 * 一条命令自检内容层与编译层（不需要 Android SDK）。
 *   node tools/check-all.mjs
 * 会做：Schema 结构自检 → 逐份试卷校验 → 编译 epak → 包体检 → web 预览构建。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { examSchema } from '../framework/src/schema.mjs';
import { validateExam } from '../framework/src/validate.mjs';
import { loadExam } from '../framework/src/load.mjs';
import { buildEpak, inspectEpak } from '../framework/src/epak.mjs';
import { buildWebHtml } from '../framework/src/webbuild.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..');
const contentDir = path.join(repo, 'content');
const buildDir = path.join(repo, 'build');

let problems = 0;
const pass = (m) => console.log('  ✓ ' + m);
const fail = (m) => {
  problems += 1;
  console.log('  ✗ ' + m);
};

function findPapers(dir) {
  const out = [];
  const walk = (d, depth) => {
    if (depth > 3 || !fs.existsSync(d)) return;
    for (const ent of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, ent.name);
      if (ent.isDirectory()) walk(p, depth + 1);
      else if (ent.name.endsWith('.json') && ent.name !== 'extract-report.json') out.push(p);
    }
  };
  walk(dir, 0);
  return out;
}

async function main() {
  console.log('== 1. Schema ==');
  const schemaText = JSON.stringify(examSchema);
  if (schemaText.includes('"sections"') && schemaText.includes('"questions"')) pass('JSON Schema 可用');
  else fail('JSON Schema 结构异常');
  fs.mkdirSync(path.join(repo, 'framework', 'schema'), { recursive: true });
  fs.writeFileSync(path.join(repo, 'framework', 'schema', 'exam.schema.json'), JSON.stringify(examSchema, null, 2));
  pass('已导出 framework/schema/exam.schema.json');

  const papers = findPapers(contentDir);
  if (!papers.length) return fail('content/ 下没找到试卷 JSON');
  console.log(`\n== 2. 逐份校验（${papers.length} 份） ==`);
  fs.mkdirSync(buildDir, { recursive: true });
  for (const file of papers) {
    const rel = path.relative(repo, file);
    const { exam, baseDir } = await loadExam(file);
    const report = validateExam(exam);
    if (!report.ok) {
      fail(`${rel} 校验失败：${report.errors.slice(0, 5).join(' | ')}`);
      continue;
    }
    pass(`${rel} 校验通过 ${JSON.stringify(report.stats)}`);
    if (report.warnings.length) console.log(`     警告 ${report.warnings.length} 条：${report.warnings.slice(0, 3).join(' | ')}`);

    console.log('   → 编译 epak');
    const { buffer, manifest } = buildEpak(exam, { baseDir });
    const epakPath = path.join(buildDir, manifest.id + '.epak');
    fs.writeFileSync(epakPath, buffer);
    pass(`   ${path.relative(repo, epakPath)} ${(buffer.length / 1024).toFixed(1)} KB`);

    const inspect = inspectEpak(buffer);
    const expect = report.stats;
    if (inspect.counts.questions !== expect.questions) fail(`   题数不一致：包 ${inspect.counts.questions} ≠ 源 ${expect.questions}`);
    else pass(`   题数一致 ${inspect.counts.questions}`);
    if (manifest.counts.questions !== inspect.counts.questions) fail('   manifest 与 exam.json 题数不一致');
    else pass('   manifest 与 exam.json 一致');
    if (inspect.counts.questionsWithAnalysis !== inspect.counts.questions) {
      console.log(`     注意：${inspect.counts.questions - inspect.counts.questionsWithAnalysis} 题没有解析`);
    } else pass('   每道题都有解析');

    console.log('   → 构建 web 预览');
    const webDir = path.join(buildDir, 'web', manifest.id);
    fs.mkdirSync(webDir, { recursive: true });
    fs.writeFileSync(path.join(webDir, 'index.html'), buildWebHtml(
      (await import('../framework/src/compile.mjs')).compileExam(exam, { baseDir })
    ));
    pass(`   ${path.relative(repo, path.join(webDir, 'index.html'))}`);

    // 同步进 App 种子包（只有主内容包会被投放）
    const assets = path.join(repo, 'android', 'app', 'src', 'main', 'assets', 'seed.epak');
    if (papers.length === 1) {
      fs.mkdirSync(path.dirname(assets), { recursive: true });
      fs.writeFileSync(assets, buffer);
      pass(`   已投放 App 种子包 ${path.relative(repo, assets)}`);
    }
  }

  console.log('\n== 3. Android 工程静态检查 ==');
  const required = [
    'android/settings.gradle.kts',
    'android/build.gradle.kts',
    'android/app/build.gradle.kts',
    'android/app/src/main/AndroidManifest.xml',
    'android/gradle/wrapper/gradle-wrapper.jar',
    'android/gradle/wrapper/gradle-wrapper.properties',
    'android/gradlew.bat',
    'android/app/src/main/assets/seed.epak'
  ];
  for (const f of required) {
    if (fs.existsSync(path.join(repo, f))) pass(f);
    else fail('缺少 ' + f);
  }
  const ktCount = (function count(dir) {
    if (!fs.existsSync(dir)) return 0;
    return fs.readdirSync(dir, { withFileTypes: true }).reduce((a, e) => a + (e.isDirectory() ? count(path.join(dir, e.name)) : e.name.endsWith('.kt') ? 1 : 0), 0);
  })(path.join(repo, 'android', 'app', 'src', 'main', 'java'));
  if (ktCount > 0) pass(`Kotlin 源文件 ${ktCount} 个`);
  else fail('没找到 Kotlin 源文件');

  console.log('\n== 4. 已删除功能核查（应全部为 0 命中） ==');
  const removedPatterns = [
    ['批注画板', /drawOn|btnDraw|ExitDraw|annotation/i],
    ['朗读音频', /speechSynthesis|audioBar|__AUDIO__|MediaPlayer/i],
    ['Word 导出', /docx|wordprocessingml|导出 Word/i],
    ['打印', /window\.print|print-sec|print-quiz/i]
  ];
  const ktFiles = (function walk(dir, acc = []) {
    if (!fs.existsSync(dir)) return acc;
    for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, ent.name);
      if (ent.isDirectory()) walk(p, acc);
      else if (/\.(kt|xml)$/.test(ent.name)) acc.push(p);
    }
    return acc;
  })(path.join(repo, 'android', 'app', 'src', 'main'));
  for (const [label, pattern] of removedPatterns) {
    const hits = ktFiles.filter((f) => pattern.test(fs.readFileSync(f, 'utf8')));
    if (hits.length === 0) pass(`${label}：无残留`);
    else fail(`${label}：仍出现在 ${hits.map((h) => path.relative(repo, h)).join(', ')}`);
  }

  console.log('\n== 5. 包内音频核查 ==');
  for (const file of papers) {
    const { exam, baseDir } = await loadExam(file);
    const { buffer, manifest } = buildEpak(exam, { baseDir });
    const hasAudio = /\.(mp3|m4a|wav|ogg|aac)$/i.test([...require_entries(buffer)].join(' '));
    if (hasAudio) fail(`${manifest.id} 包内仍有音频文件`);
    else pass(`${manifest.id} 包内没有音频文件`);
  }

  console.log('\n== 6. Kotlin 静态检查 ==');
  try {
    const { execFileSync } = await import('node:child_process');
    const out = execFileSync(process.execPath, [path.join(repo, 'tools', 'check-kotlin.mjs')], { encoding: 'utf8' });
    const lastLine = out.trim().split(/\r?\n/).pop();
    pass('check-kotlin.mjs：' + lastLine);
  } catch (e) {
    fail('check-kotlin.mjs 未通过（详见 node tools/check-kotlin.mjs 输出）');
  }

  console.log(problems === 0 ? '\n全部通过 ✓' : `\n发现 ${problems} 个问题 ✗`);
  process.exit(problems === 0 ? 0 : 1);
}

/** 列出 epak(zip) 内的文件名 */
function require_entries(buffer) {
  const names = [];
  for (let i = 0; i < buffer.length - 4; i += 1) {
    // 粗读 local file header 的文件名
    if (buffer.readUInt32LE(i) === 0x04034b50) {
      const nameLen = buffer.readUInt16LE(i + 26);
      const extraLen = buffer.readUInt16LE(i + 28);
      const name = buffer.slice(i + 30, i + 30 + nameLen).toString('utf8');
      if (name) names.push(name);
      i += 30 + nameLen + extraLen - 1;
    }
  }
  return names;
}

main().catch((e) => {
  console.error('自检异常：', e);
  process.exit(1);
});
