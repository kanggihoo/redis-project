# Result Recording Guide

실험 결과는 구현 코드와 분리해 `docs/evidence/`에 저장한다.

## Naming Rule

파일명에는 phase, scenario, strategy, VU/duration, run number, result type을 포함한다.

```text
cache-aside-200vu-60s-run1-k6.json
stampede-mutex-500vu-run2-grafana.png
fixed-window-boundary-run1-result.md
```

## Report Rule

`report.md`에는 evidence 내용을 복사하지 않고 링크와 해석을 기록한다.
