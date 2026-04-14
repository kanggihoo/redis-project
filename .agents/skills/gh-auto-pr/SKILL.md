---
name: gh-auto-pr
description: gh CLI와 PR 템플릿을 사용하여 로컬에서 테스트 리포트를 포함하는 Pull Request를 자동으로 생성하는 스킬
---

# gh-auto-pr 스킬

이 스킬은 사용자의 레포지토리 내 기본 PR 템플릿(`.github/pull_request_template.md`)을 읽고, `gh` CLI를 활용하여 자동으로 Pull Request를 생성하는 과정을 돕습니다. 특히 Spring/Gradle 기반 프로젝트에서 생성된 테스트 결과 이미지를 PR 본문에 자동으로 연결하는 역할을 합니다.

## 실행 프로토콜 (반드시 순서대로 진행)

### 1단계: 프로젝트 환경 정보 자동 수집
사용자에게 묻기 전, 전용 스크립트를 실행하여 필요한 정보를 파악합니다.
1. **스크립트 실행**: `bash .agents/skills/gh-auto-pr/scripts/collect_context.sh` 명령어를 실행하여 출력 결과를 분석합니다.
2. **정보 추출**: 출력된 `OWNER`, `REPO`, `BRANCH`, `IMAGE_INFO` 정보를 변수에 저장합니다.

### 2단계: 사용자 확인 및 엣지 케이스 점검 (Socratic Gate)
수집된 정보를 바탕으로 사용자에게 PR 생성 의사를 확인합니다.
1. **상태 확인**: "현재 `{BRANCH}` 브랜치의 변경사항이 깃허브에 모두 push 되었나요? `.github/report`에 있는 이미지(예: {파일명})를 PR 본문에 포함할까요?"
2. **제약사항 문의**: "PR 템플릿 외에 추가로 반영해야 할 특이사항이나 제목(Title)에 대한 규칙이 있나요?"

### 3단계: PR_BODY.md 임시 파일 작성
1. **템플릿 로드**: `.github/pull_request_template.md` 파일을 읽어 기본 구조로 삼습니다.
2. **내용 작성**: 최근 커밋 내역과 작업 내용을 분석하여 템플릿 항목을 채웁니다.
3. **이미지 URL 구성**: 수집한 정보를 조합하여 아래 형식으로 PR 하단에 이미지를 삽입합니다.
   - `![테스트 결과](https://raw.githubusercontent.com/{OWNER}/{REPO}/{BRANCH}/.github/report/{이미지파일명})`
4. **파일 저장**: `write_to_file`을 사용하여 최상단에 `PR_BODY.md`를 생성합니다.

### 4단계: PR 생성 및 사후 보고
1. **명령어 실행**: `gh pr create --title "[유형] PR 제목" --body-file PR_BODY.md`를 실행합니다.
2. **결과 보고**: 생성된 PR 링크를 사용자에게 공유합니다.
3. **뒷정리**: `bash .agents/skills/gh-auto-pr/scripts/cleanup.sh`를 실행하여 임시 파일을 삭제합니다.
