/**
 * 从旧版单页 HTML 中抽取试卷内容 -> 规范化 canonical JSON。
 * 只抽取「试卷 / 词汇检测 / 思维导图」；朗读音频(__AUDIO__)按需求丢弃。
 */
import fs from 'node:fs';
import path from 'node:path';

const HTML = process.argv[2] || 'C:/Users/34054/.dsh/attachments/v1/files/b4/b4d134f8a6c8e6a19bdff4841ad8aa3031123b9d7d71def416636411205c25fd/德阳五中10月自主测验英语 全卷互动精讲.html';
const OUT = process.argv[3] || 'D:/dsh_work/EA/content/deyang-2025-10';

const html = fs.readFileSync(HTML, 'utf8');

function grab(name) {
  const re = new RegExp('window\\.' + name + '=(\\{[\\s\\S]*?\\});?<\\/script>');
  const m = html.match(re);
  if (!m) throw new Error('missing window.' + name);
  return JSON.parse(m[1]);
}

const P = grab('__PAPER__');
const Q = grab('__QUIZ__');
const M = grab('__MM__');
const AUD = grab('__AUDIO__');

const audit = { sectionKeys: new Set(), questionKeys: new Set(), anaKeys: new Set(), paraKeys: new Set(), quizPartKeys: new Set(), quizItemKeys: new Set() };
const track = (set, obj) => Object.keys(obj || {}).forEach(k => set.add(k));

const KNOWN_SECTION = ['id','type','tab','title','ptitle','qnos','paras','questions','chip','struct','vocab','migrate','topic','head','hint'];
const KNOWN_Q = ['no','stem','kind','ans_letter','options','ans_text','ana','ans_zh','hint'];

function mapPara(p) {
  track(audit.paraKeys, p);
  const o = {};
  if (p.h != null && p.h !== '') o.heading = String(p.h);
  o.en = String(p.en == null ? '' : p.en);
  o.zh = String(p.zh == null ? '' : p.zh);
  return o;
}

function mapQuestion(q) {
  track(audit.questionKeys, q);
  track(audit.anaKeys, q.ana || {});
  const out = {
    no: String(q.no),
    stem: String(q.stem == null ? '' : q.stem),
    kind: String(q.kind == null ? '' : q.kind),
    answer: {
      letter: q.ans_letter == null ? null : String(q.ans_letter),
      text: q.ans_text == null ? null : String(q.ans_text),
      zh: q.ans_zh == null ? null : String(q.ans_zh),
      hint: q.hint == null ? null : String(q.hint)
    },
    options: (q.options || []).map(o => ({ label: String(o.l), text: String(o.t) })),
    analysis: {
      solutionLabel: q.ana && q.ana.solLb != null ? String(q.ana.solLb) : '解析',
      solutions: ((q.ana && q.ana.sol) || []).map(s => {
        const r = { label: String(s.lb == null ? '' : s.lb), text: String(s.t == null ? '' : s.t) };
        if (s.loc != null) r.loc = String(s.loc);
        return r;
      }),
      groups: ((q.ana && q.ana.groups) || []).map(g => ({
        key: String(g.key == null ? '' : g.key),
        label: String(g.lb == null ? '' : g.lb),
        items: (g.items || []).map(x => String(x))
      }))
    }
  };
  const unknown = Object.keys(q).filter(k => !KNOWN_Q.includes(k));
  if (unknown.length) out.legacyExtras = Object.fromEntries(unknown.map(k => [k, q[k]]));
  return out;
}

function mapSection(s) {
  track(audit.sectionKeys, s);
  const out = {
    id: String(s.id),
    type: String(s.type),
    tabLabel: String(s.tab == null ? '' : s.tab),
    chip: String(s.chip == null ? '' : s.chip),
    title: String(s.title == null ? '' : s.title),
    passageTitle: String(s.ptitle == null ? '' : s.ptitle),
    topic: String(s.topic == null ? '' : s.topic),
    qnos: (s.qnos || []).map(String),
    paras: (s.paras || []).map(mapPara),
    vocab: (s.vocab || []).map(v => {
      const r = { word: String(v.w), phonetic: String(v.ph == null ? '' : v.ph), partOfSpeech: String(v.pos == null ? '' : v.pos), zh: String(v.zh == null ? '' : v.zh) };
      const unk = Object.keys(v).filter(k => !['w','ph','pos','zh'].includes(k));
      if (unk.length) r.legacyExtras = Object.fromEntries(unk.map(k => [k, v[k]]));
      return r;
    }),
    migrations: (s.migrate || []).map(m => ({ title: String(m.t == null ? '' : m.t), detail: String(m.d == null ? '' : m.d) })),
    outline: (s.struct || []).map(x => ({ level: String(x.k), text: String(x.t) })),
    questions: (s.questions || []).map(mapQuestion)
  };
  const unknown = Object.keys(s).filter(k => !KNOWN_SECTION.includes(k));
  if (unknown.length) out.legacyExtras = Object.fromEntries(unknown.map(k => [k, s[k]]));
  if (s.head && Object.keys(s.head).length) out.legacyHead = s.head;
  if (s.hint && Object.keys(s.hint).length) out.legacyHint = s.hint;
  return out;
}

function mapQuiz(qz) {
  const parts = (qz.parts || []).map(p => {
    track(audit.quizPartKeys, p);
    const out = {
      no: String(p.no),
      name: String(p.name == null ? '' : p.name),
      desc: String(p.desc == null ? '' : p.desc),
      per: Number(p.per == null ? 1 : p.per)
    };
    if (p.box) out.box = p.box.map(String);
    if (p.segs) out.segments = p.segs.map(String);
    if (p.ans) out.answers = p.ans.map(String);
    out.items = (p.items || []).map(it => {
      track(audit.quizItemKeys, it);
      const r = { no: typeof it.no === 'number' ? it.no : String(it.no), answer: String(it.ans == null ? '' : it.ans) };
      if (it.ph != null) r.phonetic = String(it.ph);
      if (it.pos != null) r.partOfSpeech = String(it.pos);
      if (it.zh != null) r.zh = String(it.zh);
      if (it.pre != null) r.pre = String(it.pre);
      if (it.post != null) r.post = String(it.post);
      const unk = Object.keys(it).filter(k => !['no','ph','pos','zh','ans','pre','post'].includes(k));
      if (unk.length) r.legacyExtras = Object.fromEntries(unk.map(k => [k, it[k]]));
      return r;
    });
    const unk = Object.keys(p).filter(k => !['no','name','desc','per','box','segs','ans','items'].includes(k));
    if (unk.length) out.legacyExtras = Object.fromEntries(unk.map(k => [k, p[k]]));
    return out;
  });
  return { title: String(qz.title == null ? '' : qz.title), total: Number(qz.total == null ? 0 : qz.total), parts };
}

const mindmaps = [];
fs.mkdirSync(path.join(OUT, 'mindmaps'), { recursive: true });
for (const [sectionId, svg] of Object.entries(M || {})) {
  fs.writeFileSync(path.join(OUT, 'mindmaps', sectionId + '.svg'), String(svg), 'utf8');
  mindmaps.push({ sectionId, file: 'mindmaps/' + sectionId + '.svg', bytes: String(svg).length });
}

const dictEntries = Object.entries(P.dict || {}).map(([word, arr]) => ({
  word,
  phonetic: String(arr[0] == null ? '' : arr[0]),
  partOfSpeech: String(arr[1] == null ? '' : arr[1]),
  zh: String(arr[2] == null ? '' : arr[2])
}));

const exam = {
  schemaVersion: 1,
  id: 'deyang-2025-10-english',
  meta: {
    title: String(P.meta.title),
    sub: String(P.meta.sub == null ? '' : P.meta.sub),
    full: String(P.meta.full),
    paperLine: String(P.meta.paper == null ? '' : P.meta.paper),
    subject: 'english',
    language: 'zh-CN',
    source: 'legacy-html:德阳五中10月自主测验英语 全卷互动精讲.html'
  },
  sections: P.sections.map(mapSection),
  dict: dictEntries,
  quiz: mapQuiz(Q),
  mindmaps
};

fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, 'paper.json'), JSON.stringify(exam, null, 2), 'utf8');

const stats = {
  sections: exam.sections.length,
  questions: exam.sections.reduce((a, s) => a + s.questions.length, 0),
  paragraphs: exam.sections.reduce((a, s) => a + s.paras.length, 0),
  sectionVocab: exam.sections.reduce((a, s) => a + s.vocab.length, 0),
  migrations: exam.sections.reduce((a, s) => a + s.migrations.length, 0),
  outlineNodes: exam.sections.reduce((a, s) => a + s.outline.length, 0),
  dictEntries: exam.dict.length,
  quizParts: exam.quiz.parts.length,
  quizItems: exam.quiz.parts.reduce((a, p) => a + p.items.length, 0),
  quizSegmentBlanks: exam.quiz.parts.reduce((a, p) => a + (p.answers ? p.answers.length : 0), 0),
  mindmaps: exam.mindmaps.length,
  questionsWithOptions: exam.sections.reduce((a, s) => a + s.questions.filter(q => q.options.length > 0).length, 0),
  questionsWithAnalysis: exam.sections.reduce((a, s) => a + s.questions.filter(q => q.analysis.solutions.length > 0).length, 0),
  droppedAudioClips: Object.keys(AUD).length,
  legacyKeySets: Object.fromEntries(Object.entries(audit).map(([k, v]) => [k, [...v]])),
  legacyExtrasFound: []
};
exam.sections.forEach(s => {
  if (s.legacyExtras) stats.legacyExtrasFound.push('section ' + s.id + ': ' + Object.keys(s.legacyExtras).join(','));
  s.questions.forEach(q => { if (q.legacyExtras) stats.legacyExtrasFound.push('Q' + q.no + ': ' + Object.keys(q.legacyExtras).join(',')); });
});
fs.writeFileSync(path.join(OUT, 'extract-report.json'), JSON.stringify(stats, null, 2), 'utf8');

console.log('OUT=' + OUT);
console.log(JSON.stringify({ ...stats, legacyKeySets: undefined }, null, 2));
console.log('legacy key sets: ' + JSON.stringify(stats.legacyKeySets));
console.log('legacy extras: ' + (stats.legacyExtrasFound.length ? stats.legacyExtrasFound.join(' | ') : '(none)'));
console.log('paper.json bytes: ' + fs.statSync(path.join(OUT, 'paper.json')).size);
