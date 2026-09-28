import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(scriptDirectory, '../..');
const resultsPath = process.argv[2]
  ? path.resolve(projectRoot, process.argv[2])
  : path.join(scriptDirectory, 'results.json');
const summaryPath = path.join(scriptDirectory, 'summary.json');

if (!fs.existsSync(resultsPath)) {
  console.error(`Promptfoo results not found: ${path.relative(projectRoot, resultsPath)}`);
  process.exit(1);
}

const document = JSON.parse(fs.readFileSync(resultsPath, 'utf8'));
const rows = (document.results?.results ?? []).map((result) => {
  const components = result.gradingResult?.componentResults ?? [];
  const judge = components.find((component) => component.assertion?.type === 'llm-rubric');
  const metadata = result.response?.metadata ?? {};
  return {
    caseId: result.testCase?.vars?.caseId ?? `case-${result.testIdx + 1}`,
    description: result.testCase?.description ?? '',
    approach: result.provider?.label ?? result.provider?.id ?? 'unknown',
    passed: Boolean(result.success),
    score: typeof result.score === 'number' ? result.score : null,
    judgeScore: typeof judge?.score === 'number' ? judge.score : null,
    judgePassed: typeof judge?.pass === 'boolean' ? judge.pass : null,
    latencyMillis: metadata.latencyMillis ?? result.latencyMs ?? null,
    toolCallCount: metadata.toolCallCount ?? 0,
    error: result.failureReason || undefined,
  };
});

const approaches = [...new Set(rows.map((row) => row.approach))];
const summaries = approaches.map((approach) => {
  const subset = rows.filter((row) => row.approach === approach);
  const latencies = subset.map((row) => row.latencyMillis).filter(Number.isFinite).sort((a, b) => a - b);
  const medianLatencyMillis = latencies.length === 0 ? null
    : latencies.length % 2 === 1 ? latencies[(latencies.length - 1) / 2]
      : (latencies[latencies.length / 2 - 1] + latencies[latencies.length / 2]) / 2;
  const judgeScores = subset.map((row) => row.judgeScore).filter(Number.isFinite);
  return {
    approach,
    passed: subset.filter((row) => row.passed).length,
    total: subset.length,
    passRate: subset.length === 0 ? null : subset.filter((row) => row.passed).length / subset.length,
    meanJudgeScore: judgeScores.length === 0 ? null
      : judgeScores.reduce((sum, score) => sum + score, 0) / judgeScores.length,
    medianLatencyMillis,
    totalToolCalls: subset.reduce((sum, row) => sum + row.toolCallCount, 0),
  };
});

const summary = {
  evalId: document.evalId ?? null,
  generatedAt: new Date().toISOString(),
  sourceResults: path.relative(projectRoot, resultsPath).replaceAll('\\', '/'),
  rowCount: rows.length,
  approaches: summaries,
  cases: rows,
};
fs.writeFileSync(summaryPath, `${JSON.stringify(summary, null, 2)}\n`);

const columns = [
  ['Case', 39, (row) => row.caseId],
  ['Approach', 12, (row) => row.approach],
  ['Pass', 6, (row) => row.passed ? 'PASS' : 'FAIL'],
  ['Judge', 6, (row) => row.judgeScore == null ? 'n/a' : row.judgeScore.toFixed(2)],
  ['Latency', 10, (row) => row.latencyMillis == null ? 'n/a' : `${Math.round(row.latencyMillis)} ms`],
  ['Tools', 5, (row) => String(row.toolCallCount)],
];
const line = (values) => values.map((value, index) => String(value).padEnd(columns[index][1])).join(' | ');
console.log(line(columns.map(([header]) => header)));
console.log(columns.map(([, width]) => '-'.repeat(width)).join('-+-'));
for (const row of rows) console.log(line(columns.map(([, , value]) => value(row))));
console.log('\nApproach summary:');
for (const approach of summaries) {
  const latency = approach.medianLatencyMillis == null ? 'n/a' : `${Math.round(approach.medianLatencyMillis)} ms`;
  const score = approach.meanJudgeScore == null ? 'n/a' : approach.meanJudgeScore.toFixed(2);
  console.log(`- ${approach.approach}: ${approach.passed}/${approach.total} passed; mean judge ${score}; median latency ${latency}; ReAct tool calls ${approach.totalToolCalls}.`);
}
console.log(`\nSummary written to ${path.relative(projectRoot, summaryPath).replaceAll('\\', '/')}`);
