# Contributing Guide

항목을 추가하거나 수정할 때 따르는 규칙과 템플릿 모음.

---

## Frontmatter 스키마

### Skill (`SKILL.md`)

스킬 원본 파일에 이미 포함된 기본 필드는 건드리지 않음. 아래는 **이 레포에서 추가하는 메타 필드만** 명시한다.

```yaml
---
# 기본 필드 (에이전트가 읽는 필드, 원본 그대로 유지)
name: skill-name
description: 한 줄 설명. 언제 이 skill을 쓰는지.
allowed-tools: Read, Grep, Glob  # 스킬이 사용할 수 있는 도구

# --- 이 레포에서 추가하는 메타 필드 ---

origin: custom # custom | external | fork

# [선택] 외부 출처가 있는 경우 (external | fork)
source: https://github.com/original-source-url

# [선택] 영감을 얻었거나 참고한 외부 자료 (무조건 URL로 작성)
refs:
  - https://...
---
```

**상황별 필수 필드**

| origin | 필수 필드 |
|---|---|
| `custom` | `name`, `description`, `origin` |
| `external` (원본 그대로) | + `source` |
| `fork` (수정 포함) | + `source` (수정 내역은 **커밋 메시지**로 관리) |

---

### Agent (`agent-name.md`)

에이전트 원본 파일에 이미 포함된 기본 필드는 건드리지 않음. 아래는 **이 레포에서 추가하는 메타 필드만** 명시한다.

```yaml
---
# 기본 필드 (에이전트가 읽는 필드, 원본 그대로 유지)
name: agent-name
description: 이 agent가 하는 일. 어떤 키워드/상황에서 트리거되는지.
tools: Read, Grep, Glob, Bash, Edit, Write
model: inherit
skills: skill-a, skill-b, skill-c

# --- 이 레포에서 추가하는 메타 필드 ---

origin: custom # custom | external | fork

# [선택] 외부 출처가 있는 경우 (external | fork)
source_url: https://github.com/...
source_author: author-name
---
```

---

### Workflow (`workflow-name.md`)

워크플로우의 기본 필드는 `description` 하나뿐이므로 나머지는 모두 이 레포의 메타 필드다.

```yaml
---
# 기본 필드 (에이전트가 읽는 필드, 원본 그대로 유지)
description: 이 workflow가 하는 일. `/command` 형태로 사용.

# --- 이 레포에서 추가하는 메타 필드 ---

origin: custom # custom | external | fork

# [선택] 외부 출처가 있는 경우 (external | fork)
source_url: https://github.com/...
source_author: author-name
---
```

---

## 추가 프로세스

### 새 Skill 추가

1. `skills/skill-name/` 디렉토리 생성
2. `SKILL.md` 작성 (위 초경량 스키마 적용)
3. 필요한 경우 `references/`, `scripts/` 서브디렉토리 추가
4. `ARCHITECTURE.md` 업데이트 (Skills 테이블에 행 추가 및 통계 업데이트)
5. 이 skill을 사용해야 할 기존 agent가 있으면 해당 agent의 `skills:` 필드에 추가

### 새 Agent 추가

1. `agents/agent-name.md` 작성 (위 스키마 적용)
2. 이 agent가 사용할 skills의 `SKILL.md`를 확인 — 실제로 존재하는지 검증
3. `ARCHITECTURE.md` 업데이트:
   - Agents 테이블에 행 추가
   - Quick Reference 테이블 업데이트 (해당되는 경우)
   - Statistics 섹션 숫자 업데이트
4. 이 agent를 호출하는 workflow가 있으면 해당 workflow 파일 내용 확인

```
예시: data-scientist agent 추가 시
- agents/data-scientist.md 생성
- skills: python-patterns 존재 여부 확인
- ARCHITECTURE.md > Agents 테이블에 추가
```

### 새 Workflow 추가

1. `workflows/workflow-name.md` 작성 (위 스키마 적용)
2. workflow 내부에서 호출하는 agent, skill 이름이 실제로 존재하는지 확인
3. `ARCHITECTURE.md` 업데이트:
   - Workflows 테이블에 행 추가
   - Statistics 섹션 숫자 업데이트

---

## 수정 기록 방법

**앞으로 수정 기록은 별도의 필드 없이 Git 커밋 메시지로만 관리합니다.**

- **규칙**: `git-commit` 스킬의 Conventional Commits 표준을 반드시 따릅니다.
- **스코프 형식**: `타입(skill|agent|workflow/이름): 설명`

```bash
feat(skill/git-commit): add breaking change detection
fix(skill/api-patterns): correct REST status code table
feat(agent/frontend-specialist): add deep design thinking phase
fix(agent/debugger): remove outdated tool reference
feat(workflow/create): add project planning step
```

### 특정 항목 이력 조회

```bash
# 특정 스킬 이력 (경로 기반)
git log --oneline -- skills/git-commit/

# 변경 내용까지 확인
git log -p -- skills/git-commit/

# 커밋 메시지로 검색 (경로 몰라도 됨)
git log --oneline --grep="skill/git-commit"
```

---

## 메타데이터 일괄 수정 도구

`SKILL.md`의 프론트매터(Frontmatter)를 표준에 맞게 일괄 수정하거나 새로운 필드를 추가할 때 다음 도구를 사용합니다.

### 사용법 (`scripts/skill_meta_editor.py`)

이 스크립트는 하나 이상의 파일 또는 디렉토리를 대상으로 동적인 키-값(key=value) 수정을 지원합니다.

```bash
# 1. 특정 스킬의 메타데이터 업데이트
python scripts/skill_meta_editor.py --path skills/api-patterns/SKILL.md version=1.2.3

# 2. 모든 스킬의 출처(origin, source)를 일괄 설정
python scripts/skill_meta_editor.py --path skills origin=external source="https://github.com/vudovn/antigravity-kit"

# 3. 여러 경로를 동시에 지정하여 수정
python scripts/skill_meta_editor.py --path skills/git-commit skills/clean-code/SKILL.md license=MIT
```

- `--path`: 수정할 파일 경로 혹은 디렉토리 (여러 개 지정 가능)
- `key=value`: 업데이트할 필드와 값 (개수 제한 없음)
- **주의**: `templates/` 디렉토리 내의 `SKILL.md`는 자동으로 제외됩니다.

---

## 다른 프로젝트로 복사할 때

`.agent/` 폴더 전체를 복사하면 됨. 각 파일의 frontmatter에 출처가 기록되어 있으므로 별도 문서 없이 히스토리가 유지됨.

복사 후 확인 사항:

- `ARCHITECTURE.md`의 내용이 실제 파일 구성과 일치하는지 확인
- 프로젝트 특성에 맞지 않는 agent/skill/workflow는 제거
- 제거한 경우 `ARCHITECTURE.md` Statistics 업데이트
