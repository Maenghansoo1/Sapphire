# 작업 단위 브랜치 가이드

각 기능이나 수정 작업마다 새로운 브랜치를 생성해서 작업합니다.

예시:
```text
feature/login
feature/group
fix/login-error
refactor/user-service
```

## 1. 최신 main 가져오기

```bash
git switch main
git pull origin main
```

## 2. 작업 브랜치 생성

```bash
git switch -c feature/login
```

브랜치 이름은 작업 목적에 맞게 작성합니다.

```text
feature/기능명
fix/버그명
refactor/작업명
```

## 3. 작업 후 커밋

```bash
git add .
git commit -m "feat(auth): 로그인 기능 추가"
```

## 4. 원격 저장소에 브랜치 업로드

```bash
git push origin feature/login
```

## 5. PR 생성

GitHub에서

```text
feature/login → main
```

방향으로 Pull Request를 생성합니다.

GitHub CLI를 사용하는 경우:

```bash
gh pr create --base main --head feature/login
```

팀원이 코드를 확인한 뒤 승인하면 Merge합니다.

## 기본 흐름

```text
main
 ↓
작업 브랜치 생성
 ↓
코드 작성
 ↓
commit
 ↓
push
 ↓
PR
 ↓
review
 ↓
main에 merge
```

하나의 브랜치에서는 하나의 기능이나 작업만 진행하는 것을 권장합니다.

너무 세세한 작업까지 브렌치를 파지 마세요.