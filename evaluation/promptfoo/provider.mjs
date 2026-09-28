import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

export default class ApiDependabotProvider {
  constructor(options = {}) {
    this.config = options.config ?? {};
    this.mode = this.config.mode;
  }

  id() {
    return `api-dependabot-${this.mode ?? 'unknown'}`;
  }

  async callApi(_prompt, context = {}) {
    const vars = context.vars ?? {};
    if (!['vanilla-rag', 'react'].includes(this.mode)) {
      return { error: `Unsupported API Dependabot mode: ${this.mode}` };
    }
    for (const name of ['oldSpec', 'newSpec', 'repository', 'question']) {
      if (!vars[name]) return { error: `Missing required case variable: ${name}` };
    }

    const jar = path.join(projectRoot, 'target', 'api-dependabot-0.1.0-SNAPSHOT.jar');
    const args = [
      '-jar', jar,
      `--old=${path.resolve(projectRoot, vars.oldSpec)}`,
      `--new=${path.resolve(projectRoot, vars.newSpec)}`,
      `--repo=${path.resolve(projectRoot, vars.repository)}`,
      `--question=${vars.question}`,
      '--top-k=3',
      `--${this.mode === 'react' ? 'react' : 'answer'}`,
      '--format=json',
    ];
    const child = spawnSync(process.env.JAVA ?? 'java', args, {
      cwd: projectRoot,
      encoding: 'utf8',
      env: { ...process.env, SPRING_AI_MODEL_CHAT: process.env.SPRING_AI_MODEL_CHAT || 'openai' },
      timeout: Number(this.config.timeoutMs ?? 180_000),
      maxBuffer: 4 * 1024 * 1024,
      windowsHide: true,
    });
    if (child.error) return { error: `Could not run Java CLI: ${child.error.message}` };
    if (child.status !== 0) {
      return { error: `Java CLI exited with ${child.status}: ${(child.stderr || child.stdout).slice(-4000)}` };
    }

    let result;
    try {
      result = JSON.parse(child.stdout.trim());
    } catch {
      return { error: `Java CLI did not return JSON. stderr: ${(child.stderr ?? '').slice(-4000)}` };
    }
    if (result.status !== 'success' || result.mode !== this.mode) {
      return { error: result.error ?? `Unexpected CLI result status/mode: ${result.status}/${result.mode}`, metadata: result };
    }
    return {
      output: result.answer,
      metadata: {
        mode: result.mode,
        latencyMillis: result.latencyMillis,
        toolCallCount: result.toolCallCount ?? 0,
        toolTrace: result.toolTrace ?? [],
        retrievedEvidence: result.retrievedEvidence ?? [],
        evidence: result.evidence ?? [],
      },
    };
  }
}
