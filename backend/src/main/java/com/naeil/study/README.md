# com.naeil.study

백엔드 애플리케이션의 루트 패키지.

```
com.naeil.study
├── StudyBackendApplication.java   진입점
├── common/                        여러 도메인이 함께 쓰는 것
└── session/                       학습 세션 도메인
```

## 패키지를 나누는 기준

계층(controller/service/repository)이 아니라 **도메인**이 먼저 온다.

```
좋음:  session/controller, document/controller
나쁨:  controller/SessionController, controller/DocumentController
```

한 기능을 고칠 때 열어야 할 파일들이 한 폴더에 모이게 하려는 의도다.
도메인이 늘어나면 `session`과 같은 형태로 `document`, `analysis`, `topic`,
`curriculum`, `quiz`, `storage`를 추가한다.

## common 에 무엇을 두는가

**둘 이상의 도메인이 실제로 쓰는 것**만 둔다.
"언젠가 공통이 될 것 같아서" 미리 옮기지 않는다. 두 번째 사용처가 생겼을 때 옮긴다.
