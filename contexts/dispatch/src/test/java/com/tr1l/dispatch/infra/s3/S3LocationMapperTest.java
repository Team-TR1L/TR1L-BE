package com.tr1l.dispatch.infra.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S3LocationMapperTest {

    private S3LocationMapper s3LocationMapper;
    private String sampleJson;

    @BeforeEach
    void setUp() {
        // 실제 Spring Context 없이도 테스트 가능하도록 직접 주입
        s3LocationMapper = new S3LocationMapper(new ObjectMapper());

        sampleJson = """
            [
                {"key": "EMAIL", "bucket": "maru-test01", "s3_key": "2025-12/EMAIL.html"},
                {"key": "SMS", "bucket": "maru-test01", "s3_key": "2025-12/SMS.txt"}
            ]
            """;
    }

    @Test
    @DisplayName("성공: EMAIL 채널에 해당하는 bucket과 s3Key를 정확히 추출한다")
    void extractLocation_Success_Email() {
        // given
        ChannelType channel = ChannelType.EMAIL;

        // when
        S3LocationMapper.S3LocationDTO result = s3LocationMapper.extractLocation(sampleJson, channel);

        // then
        assertThat(result.key()).isEqualTo("EMAIL");
        assertThat(result.bucket()).isEqualTo("maru-test01");
        assertThat(result.s3Key()).isEqualTo("2025-12/EMAIL.html");
    }

    @Test
    @DisplayName("성공: SMS 채널에 해당하는 정보를 추출하며 대소문자를 구분하지 않는다")
    void extractLocation_Success_Sms_CaseInsensitive() {
        // given
        ChannelType channel = ChannelType.SMS;

        // when
        S3LocationMapper.S3LocationDTO result = s3LocationMapper.extractLocation(sampleJson, channel);

        // then
        assertThat(result.key()).isEqualTo("SMS");
        assertThat(result.s3Key()).isEqualTo("2025-12/SMS.txt");
    }

    @Test
    @DisplayName("실패: JSON에 없는 채널을 요청하면 RuntimeException이 발생한다")
    void extractLocation_Fail_ChannelNotFound() {
        // given
        String jsonOnlyEmail = "[{\"key\": \"EMAIL\", \"bucket\": \"b\", \"s3_key\": \"k\"}]";
        ChannelType channel = ChannelType.SMS;

        // when & then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                s3LocationMapper.extractLocation(jsonOnlyEmail, channel)
        );
        assertThat(exception.getMessage()).contains("S3 정보를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("실패: 잘못된 형식의 JSON 입력 시 예외가 발생한다")
    void extractLocation_Fail_InvalidJson() {
        // given
        String invalidJson = "{ \"not\": \"an array\" }";

        // when & then
        assertThrows(RuntimeException.class, () ->
                s3LocationMapper.extractLocation(invalidJson, ChannelType.EMAIL)
        );
    }

    @Test
    @DisplayName("실패: 빈 문자열이나 null 입력 시 IllegalArgumentException이 발생한다")
    void extractLocation_Fail_EmptyInput() {
        assertThrows(IllegalArgumentException.class, () ->
                s3LocationMapper.extractLocation("", ChannelType.EMAIL)
        );
        assertThrows(IllegalArgumentException.class, () ->
                s3LocationMapper.extractLocation(null, ChannelType.EMAIL)
        );
    }
}