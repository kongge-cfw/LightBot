// commitlint 配置：对齐项目根 CLAUDE.md 中 Git 规范（feat/fix/docs/refactor/perf/test/chore/style）
// scope 取模块名（agent / workflow / tool / rag / common / ui / chat / knowledge / platform 等）
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'docs', 'style', 'refactor', 'perf', 'test', 'chore', 'ci', 'build'],
    ],
    'subject-case': [0],
    'subject-full-stop': [0],
    'header-max-length': [2, 'always', 100],
  },
}
