#!/bin/bash

# .agents/skills/gh-auto-pr/scripts/cleanup.sh
# 사용 완료된 PR_BODY.md 파일을 삭제합니다.

if [ -f "PR_BODY.md" ]; then
  rm "PR_BODY.md"
  echo "SUCCESS: PR_BODY.md has been deleted."
else
  echo "INFO: No PR_BODY.md found to delete."
fi
