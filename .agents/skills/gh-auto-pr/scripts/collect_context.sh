#!/bin/bash

# .agents/skills/gh-auto-pr/scripts/collect_context.sh
# AI가 PR 생성을 위한 환경 정보와 Git 작업 내역을 한꺼번에 파악하도록 합니다.

echo "### REPO_INFO ###"
GH_INFO=$(gh repo view --json owner,name -q '.owner.login + "/" + .name' 2>/dev/null)
if [ -z "$GH_INFO" ]; then
  # gh가 실패하면 git remote에서 추출 시도
  REMOTE_URL=$(git remote get-url origin 2>/dev/null)
  echo "REMOTE_URL: $REMOTE_URL"
else
  echo "FULL_NAME: $GH_INFO"
fi

echo "### BRANCH_INFO ###"
echo "BRANCH: $(git branch --show-current 2>/dev/null || echo "unknown")"

echo "### GIT_STATUS (Modified Files) ###"
git status -s

echo "### RECENT_COMMITS ###"
git log -n 5 --oneline 2>/dev/null

echo "### IMAGE_INFO ###"
if [ -d ".github/report" ]; then
  ls .github/report
else
  echo "NO_DIR"
fi

echo "### CLEANUP_CHECK ###"
if [ -f "PR_BODY.md" ]; then
  echo "PR_BODY_EXISTS: true"
else
  echo "PR_BODY_EXISTS: false"
fi
