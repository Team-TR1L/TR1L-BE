## 📝작업 내용

- 메시지 읽음 처리 리팩토링
    - ReadStatus 도메인 사용 X (삭제는 안 함)
    - ChatParticipant 도메인에 마지막으로 읽은 메시지 ID 필드 추가

<br/>

- 채팅 메시지 전송시 SSE 전송해 새로고침 없이 안 읽은 메시지 개수 업데이트되도록 수정
    - SseController 생성
    - SseService 생성
    - ChatService에 코드 추가

<br/>

## 👀변경 사항

- SecurityConfigs.java
    - myFilter() 부분에 SSE URL 추가
        - /api/sse/subscribe/**

<br/>


## #️⃣관련 이슈

- closes # 