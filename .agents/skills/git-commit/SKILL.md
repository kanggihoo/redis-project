---
name: git-commit
description: 'Execute git commit with conventional commit message analysis, intelligent staging, and message generation. Use when user asks to commit changes, create a git commit, or mentions "/commit". Supports: (1) Auto-detecting type and scope from changes, (2) Generating conventional commit messages from diff, (3) Interactive commit with optional type/scope/description overrides, (4) Intelligent file staging for logical grouping'
license: MIT
allowed-tools: Bash
origin: fork
source: https://github.com/github/awesome-copilot
refs:
  - https://www.conventionalcommits.org/en/v1.0.0/
---

# Git Commit with Conventional Commits

## Overview

Create standardized, semantic git commits using the Conventional Commits specification. Analyze the actual diff to determine appropriate type, scope, and message.

## Conventional Commit Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

## Commit Types

| Type       | Purpose                        |
| ---------- | ------------------------------ |
| `feat`     | New feature                    |
| `fix`      | Bug fix                        |
| `docs`     | Documentation only             |
| `style`    | Formatting/style (no logic)    |
| `refactor` | Code refactor (no feature/fix) |
| `perf`     | Performance improvement        |
| `test`     | Add/update tests               |
| `build`    | Build system/dependencies      |
| `ci`       | CI/config changes              |
| `chore`    | Maintenance/misc               |
| `revert`   | Revert commit                  |

## Breaking Changes

```
# Exclamation mark after type/scope
feat!: remove deprecated endpoint

# BREAKING CHANGE footer
feat: allow config to extend other configs

BREAKING CHANGE: `extends` key behavior changed
```

## Workflow

### 1. Check Status

```bash
# Verify what has changed and what is already staged
git status --porcelain
```

### 2. Stage Files

Stage only the files that belong to one logical change:

```bash
# Stage specific files
git add path/to/file1 path/to/file2

# Stage by pattern
git add *.test.*
git add src/components/*

# Interactive staging (hunk-level control)
git add -p
```

**Never commit secrets** (.env, credentials.json, private keys).

### 3. Analyze Staged Diff

```bash
# Review exactly what will be committed
git diff --staged
```

If nothing is staged yet, fall back to the working tree diff to understand the full scope of changes:

```bash
git diff
```

### 4. Generate Commit Message

Analyze the diff to determine:

- **Type**: What kind of change is this?
- **Scope**: What area/module is affected?
- **Description**: One-line summary of what changed (present tense, imperative mood, <72 chars)

### Commit Shape

- Prefer one logical change per commit.
- Split independent changes into separate commits when possible.
- Group changes only when they serve one clear purpose and would be hard to understand apart.
- Keep the subject short and specific.
- Use the body only when extra context helps explain why the change was made.
- Format the body as short bullet points rather than long paragraphs.

### Subject and Body Guidance

- Subject line: one-line summary, imperative mood, specific, and ideally under 72 characters.
- Body: explain the motivation, tradeoffs, or key implementation details.
- Body should answer "why" and "what changed" more than "every file that changed".
- If the change is simple, a subject line alone is enough.
- If the change is complex, use 2-5 short bullets.

### Suggested Message Template

```text
<type>(<scope>): <short summary>

- <key change or reason>
- <key change or reason>
- <key change or reason>
```

Example:

```text
test(auth): migrate controller tests to MockMvcTester

- remove addFilters=false anti-pattern
- inject CustomUserDetails with springSecurity()
- add TestSecurityConfig to avoid LogoutFilter interference
```

### Scope Guidance

- Include a scope when the affected module or area is clear.
- Omit the scope when the change is broad or the scope would not add clarity.
- Prefer stable module names over file names.

### 5. Execute Commit

```bash
# Single line
git commit -m "<type>(<scope>): <description>"

# Multi-line with body/footer
git commit -m "$(cat <<'EOF'
<type>(<scope>): <description>

<optional body>

<optional footer>
EOF
)"
```

## Best Practices

- One logical change per commit
- Present tense: "add" not "added"
- Imperative mood: "fix bug" not "fixes bug"
- Reference issues: `Closes #123`, `Refs #456`
- Keep the subject under 72 characters when possible
- Keep the body concise and bullet-based

## Git Safety Protocol

- NEVER update git config
- NEVER run destructive commands (--force, hard reset) without explicit request
- NEVER skip hooks (--no-verify) unless user asks
- NEVER force push to main/master
- If commit fails due to hooks, fix and create NEW commit (don't amend)
