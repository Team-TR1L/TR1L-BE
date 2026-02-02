---
title: 프로젝트 Team Convention
parent: TR1L (Overview)
nav_order: 10
---

## 팀 규칙
- 데일리 스크럼 13시 진행
- 스프린트 목표 달성을 위한 진행 상황 공유, 장애물 식별, 다음 날 업무 계획 조율
- AI 코드 리뷰 활용을 감안
- PR 단위 :File Changes 10 ~ 20개

- 소나큐브
  - 지수 미설정
  - 코드 분석 용으로 활용
- 프로젝트 초기에는 main branch 병합 보호
- 차후 Hotfix가 이루어질 가능성이 있는 프로젝트 후반에는 main에 바로 병합
- main branch에서 릴리즈 버전 관리
- major, minor, patch를 사용한 릴리즈 노트 자동화화

---

## 개발 컨벤션
- `Git Branch`
    - **main** : 운영 및 배포
    - **develop** : 개발 통합
    - **{ticket-number}** : 이슈 단위 기능 작업

- `Commit`
  - {type}: 작업 내용
  - ex) feat: kafka 비동기 처리 기능 구현

| 타입         | 설명                                    |
|------------|---------------------------------------|
| `feat`     | 기능 (새로운 기능)                           |
| `fix`      | 버그 (버그 수정)                            |
| `refactor` | 리팩토링                                  |
| `test`     | 테스트 (테스트 코드 추가, 수정, 삭제)               |
| `docs`     | 문서 수정 (문서 추가, 수정, 삭제, README)         |
| `chore`    | 기타 변경사항 (빌드 스크립트 수정, assets, 패키지 매니저) |
| `init`     | 초기 생성                                 |
| `style`    | 스타일 (코드 형식, 세미콜론 추가)                  |
| `design`   | CSS 등 사용자 UI 디자인 변경                   |
| `rename`   | 파일, 폴더 명을 수정하거나 옮기는 작업                |
| `remove`   | 파일을 삭제하는 작업                           |


---

## Code
 - `변수 (Variables)`
    - 명사 형태로 작성
   - 카멜 케이스(camelCase)로 작성
   - 부울 타입(Boolean)의 경우 의문형으로 작성
   - 예시: isDialogVisible, hasDataLoaded
   - 변수명 예시:
     - `id`, `password`, `userName`
     - `isLoading`, `isUserAuthenticated`
   - **LocalDateTime**: 접미사에 Date 혹은 At를 붙임임

- `메서드 (Method)`
    - 동사 형태로 작성
    - 카멜 케이스(camelCase)로 작성
    - 함수명이 동작이나 상태를 명확히 표현
    - 예시: getId(), saveUser()
    - event, design과 같은 이중적인 단어를 가지는 단어는 지양
    - 메소드의 부수효과를 구체적으로 설명
     ```java
     void getTemp() {
       Object temp = findTemp();
       if (temp == null) {
          temp = new Temp();
       }
       return temp;
    }
  ```

  - 해당 예시에서, 단순히 Temp를 조회하는 것이 아니고 비어있으면 새롭게 생성하는 역할을 하고 있음               따라서 getTemp 보다 getOrCreateTemp() 가 적절 
  - 단, 위는 예시일 뿐 한 개의 메소드는 한 개의 역할만 하는 것을 지향

 

### 1. 클래스(Class)
- 각 패키지명을 접미사에 명시

- PascalCase로 작성

- and·or와 같은 접속사를 사용하지 않고 25자 내외로 작성

- 구현체의 경우 ~ Impl를 접미사에 추가 (ex. UserServiceImpl, UserRepositoryImpl)

- 클래스 이름 예시:
  - **controller**: UserController
  - **service**: UserService
  - **repository**: UserRepository
  - **dto/request**: UserRequest
  - **dto/response**: UserResponse
  - **entity**: User
  - **config**: WebConfig
  - **exception**: UserNotFoundException

 

### 2. 패키지명 (Package Names)

- 소문자로 작성
- 단수형 사용
- 예시: config, domain
- 상수 (Constants), ENUM
- 대문자로 작성
- 단어 사이에 언더스코어(_)를 사용하여 구분
- 예시: MAX_RETRY_COUNT, TIMEOUT_DURATION

 

### 3. DB 테이블
- snake_case 사용
### 4.URL
  - URL은 RESTful API 설계 가이드에 따라 작성
  - HTTP Method로 구분할 수 있는 get, put 등의 행위는 url에 표현하지 않음
  - 마지막에 / 를 포함하지 않음
  - _ 대신 를 사용

- 소문자를 사용
- 확장자는 포함하지 않음
- 복수형을 사용
```java
/api/auth
/api/users
/api/meetings
/api/books
```
 

### 5. Entity
- Entity 생성 시 @Table, @Column 등 어노테이션을 모두 사용
- 이유는 코드 상의 변경이 발생해도 테이블 상과 매핑을 유지하기 위함
- 또한 엔티티에 필드를 작성할 때는
  - default (db의 super key 개념에 해당하는 애들)
  - information (db의 일반 행)
  - relations (다른 테이블과의 매핑)

    
### 6. 주석 template - 인텔리제이 기준
- `Intellij → 설정 → 에디터 → 라이브 템플릿`


```java
/*==========================
*
*$method$
*
* @parm $parm$
* @return $type$
* @author $user$
* @version 1.0.0
* @date $date$
*
==========================**/
```
