# analysis/config 작업 절차

## 공급자를 바꿀 때

```
1. 새 구현체를 client 패키지에 만든다
2. 여기서 설정값(ai.provider 등)으로 고르게 한다
3. backend/README.md 의 환경변수 표를 갱신한다
4. docs/api/analysis-api.md 의 설정 표를 갱신한다
```

공급자가 둘 이상이 되면 `if (apiKey.isBlank())` 분기가 길어진다.
그때 팩터리를 분리한다. 지금(하나)은 그대로 둔다.

## 설정값을 추가할 때

```
1. application.yml 에 환경변수 형태로 추가한다
2. 기본값을 준다 (로컬 개발에서 바로 뜨게)
3. backend/README.md 환경변수 표에 한 줄 추가
```
