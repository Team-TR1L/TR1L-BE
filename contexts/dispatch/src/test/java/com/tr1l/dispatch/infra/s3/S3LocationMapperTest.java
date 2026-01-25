package com.tr1l.dispatch.infra.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class S3LocationMapperTest {

    private S3LocationMapper s3LocationMapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        s3LocationMapper = new S3LocationMapper(objectMapper);
    }

    @Test
    @DisplayName("성공: 채널에 맞는 S3 위치 정보를 정확히 추출한다")
    void extractLocation_Success() {
        // given
        String jsonb = """
                [
                    {"key": "EMAIL", "bucket": "my-bucket", "s3_key": "path/to/email"},
                    {"key": "SMS", "bucket": "my-bucket", "s3_key": "path/to/sms"}
                ]
                """;
        ChannelType channel = ChannelType.EMAIL;

        // when
        S3LocationMapper.S3LocationDTO result = s3LocationMapper.extractLocation(jsonb, channel);

        // then
        assertThat(result.key()).isEqualTo("EMAIL");
        assertThat(result.bucket()).isEqualTo("my-bucket");
        assertThat(result.s3Key()).isEqualTo("path/to/email");
    }

    @Test
    @DisplayName("유지: JSON에 해당 채널이 없으면 빈 DTO를 반환하고 멈추지 않는다")
    void extractLocation_NotFound_ReturnsEmptyDto() {
        // given
        String jsonb = "[{\"key\": \"SMS\", \"bucket\": \"b\", \"s3_key\": \"k\"}]";
        ChannelType channel = ChannelType.EMAIL; // JSON에는 SMS만 있음

        // when
        S3LocationMapper.S3LocationDTO result = s3LocationMapper.extractLocation(jsonb, channel);

        // then
        assertThat(result.key()).isEmpty();
        assertThat(result.bucket()).isEmpty();
        assertThat(result.s3Key()).isEmpty();
    }

    @Test
    @DisplayName("방어: 잘못된 형식의 JSON이 들어와도 에러를 던지지 않고 빈 DTO를 반환한다")
    void extractLocation_InvalidJson_ReturnsEmptyDto() {
        // given
        String invalidJson = "{ \"this is\": \"not a list\" }";
        ChannelType channel = ChannelType.EMAIL;

        // when
        S3LocationMapper.S3LocationDTO result = s3LocationMapper.extractLocation(invalidJson, channel);

        // then
        assertThat(result).isNotNull();
        assertThat(result.key()).isEmpty();
    }

    @Test
    @DisplayName("방어: JSON이 null이거나 빈 문자열이면 빈 DTO를 반환한다")
    void extractLocation_EmptyInput_ReturnsEmptyDto() {
        // when
        S3LocationMapper.S3LocationDTO resultNull = s3LocationMapper.extractLocation(null, ChannelType.EMAIL);
        S3LocationMapper.S3LocationDTO resultEmpty = s3LocationMapper.extractLocation("", ChannelType.EMAIL);

        // then
        assertThat(resultNull.key()).isEmpty();
        assertThat(resultEmpty.key()).isEmpty();
    }

    @Test
    @DisplayName("extractValueByChannel: 정상적으로 value 값을 추출한다")
    void extractValueByChannel_Success() {
        // given
        String json = "[{\"key\": \"EMAIL\", \"value\": \"some-value\"}]";

        // when
        String value = s3LocationMapper.extractValueByChannel(json, ChannelType.EMAIL);

        // then
        assertThat(value).isEqualTo("some-value");
    }
}