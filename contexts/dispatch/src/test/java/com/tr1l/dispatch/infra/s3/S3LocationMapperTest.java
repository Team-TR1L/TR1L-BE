package com.tr1l.dispatch.infra.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class S3LocationMapperTest {

    /**
     * 실제 JSON 파싱을 검증해야 하므로 Mock이 아닌 real ObjectMapper 사용
     */
    private ObjectMapper objectMapper;

    /**
     * 테스트 대상 클래스
     */
    private S3LocationMapper mapper;

    @BeforeEach
    void setUp() {
        // 각 테스트 간 상태 공유 방지를 위해 매번 새 인스턴스 생성
        objectMapper = new ObjectMapper();
        mapper = new S3LocationMapper(objectMapper);
    }

    /* ------------------------------------------------------------------
     * extractValueByChannel
     *
     * - 단순 key/value JSON 배열에서
     *   channelType에 해당하는 value를 추출하는 메서드
     * ------------------------------------------------------------------ */

    @Test
    void extractValueByChannel_success() {
        // given
        // EMAIL, SMS 두 채널이 모두 존재하는 정상 JSON
        String json = """
            [
              {"key": "EMAIL", "value": "email-template"},
              {"key": "SMS", "value": "sms-template"}
            ]
            """;

        // when
        // EMAIL 채널에 해당하는 value 추출
        String result = mapper.extractValueByChannel(json, ChannelType.EMAIL);

        // then
        // 정확한 value가 반환되어야 한다
        assertThat(result).isEqualTo("email-template");
    }

    @Test
    void extractValueByChannel_channelNotFound_returnsNull() {
        // given
        // EMAIL 채널이 존재하지 않는 JSON
        String json = """
            [
              {"key": "SMS", "value": "sms-template"}
            ]
            """;

        // when
        String result = mapper.extractValueByChannel(json, ChannelType.EMAIL);

        // then
        // 해당 채널이 없으면 null 반환이 정책
        assertThat(result).isNull();
    }

    @Test
    void extractValueByChannel_nullJson_returnsNull() {
        // when
        // 입력 JSON 자체가 null
        String result = mapper.extractValueByChannel(null, ChannelType.EMAIL);

        // then
        // 조기 리턴 정책에 따라 null
        assertThat(result).isNull();
    }

    @Test
    void extractValueByChannel_blankJson_returnsNull() {
        // when
        // 공백 문자열 입력
        String result = mapper.extractValueByChannel("   ", ChannelType.EMAIL);

        // then
        // 유효하지 않은 JSON이므로 null 반환
        assertThat(result).isNull();
    }


    /* ------------------------------------------------------------------
     * extractLocationValueByChannel
     *
     * - S3Location(JSONB)을 파싱하여
     *   특정 채널의 S3 URL을 생성하는 메서드
     * ------------------------------------------------------------------ */

    @Test
    void extractLocationValueByChannel_success() {
        // given
        // EMAIL 채널에 대한 S3 정보가 존재하는 정상 JSON
        String json = """
            [
              {
                "key": "EMAIL",
                "bucket": "my-bucket",
                "s3_key": "templates/email.html"
              }
            ]
            """;

        // when
        String result = mapper.extractLocationValueByChannel(json, ChannelType.EMAIL);

        // then
        // S3 URL 규칙에 맞게 정확히 조합되어야 한다
        assertThat(result)
                .isEqualTo("https://my-bucket.s3.ap-northeast-2.amazonaws.com/templates/email.html");
    }

    @Test
    void extractLocationValueByChannel_channelNotFound_returnsNull() {
        // given
        // 요청 채널(EMAIL)에 해당하는 S3 설정이 없는 JSON
        String json = """
            [
              {
                "key": "SMS",
                "bucket": "sms-bucket",
                "s3_key": "templates/sms.txt"
              }
            ]
            """;

        // when
        String result = mapper.extractLocationValueByChannel(json, ChannelType.EMAIL);

        // then
        // 정책상 예외를 던지지 않고 null 반환
        assertThat(result).isNull();
    }

    @Test
    void extractLocationValueByChannel_nullJson_returnsNull() {
        // when
        // JSONB 컬럼 자체가 null인 경우
        String result = mapper.extractLocationValueByChannel(null, ChannelType.EMAIL);

        // then
        // 조기 리턴
        assertThat(result).isNull();
    }

    @Test
    void extractLocationValueByChannel_emptyArray_returnsNull() {
        // when
        // 빈 JSON 배열 입력
        String result = mapper.extractLocationValueByChannel("[]", ChannelType.EMAIL);

        // then
        // 처리 대상 데이터가 없으므로 null
        assertThat(result).isNull();
    }

    @Test
    void extractLocationValueByChannel_blankJson_returnsNull() {
        // when
        // 공백 문자열 입력
        String result = mapper.extractLocationValueByChannel("   ", ChannelType.EMAIL);

        // then
        // 유효하지 않은 JSON → null 반환
        assertThat(result).isNull();
    }

    @Test
    void extractLocationValueByChannel_invalidJson_throwsDomainException() {
        // given
        // JSON 파싱이 불가능한 문자열
        String invalidJson = "{ invalid json }";

        // when / then
        // 예상치 못한 파싱 오류는 도메인 예외로 감싸서 던져야 한다
        assertThatThrownBy(() ->
                mapper.extractLocationValueByChannel(invalidJson, ChannelType.EMAIL)
        )
                .isInstanceOf(DispatchDomainException.class)
                .satisfies(ex -> {
                    DispatchDomainException dde = (DispatchDomainException) ex;
                    // 정확한 에러 코드 검증
                    assertThat(dde.errorCode())
                            .isEqualTo(DispatchErrorCode.S3_URL_FAILED);
                });
    }
}
