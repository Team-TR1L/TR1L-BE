package com.tr1l.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MaskingUtilTest {

    // 이름 마스킹: 빈 문자열/공백은 빈 값으로 반환
    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    @DisplayName("이름 마스킹: 공백 입력은 빈 문자열 반환")
    void maskingName_blank_returnsEmpty(String input) {
        assertEquals("", MaskingUtil.maskingName(input));
    }

    // 이름 마스킹: null은 빈 값으로 반환
    @Test
    @DisplayName("이름 마스킹: null 입력은 빈 문자열 반환")
    void maskingName_null_returnsEmpty() {
        assertEquals("", MaskingUtil.maskingName(null));
    }

    // 이름 마스킹: 가운데 글자 마스킹 규칙 검증
    @ParameterizedTest
    @CsvSource({
            "A, A",
            "AB, A*",
            "ABC, A*C",
            "Alice, A***e"
    })
    @DisplayName("이름 마스킹: 가운데 글자 마스킹")
    void maskingName_masksMiddle(String input, String expected) {
        assertEquals(expected, MaskingUtil.maskingName(input));
    }

    // 전화번호 마스킹: 빈 문자열/공백은 빈 값으로 반환
    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    @DisplayName("전화번호 마스킹: 공백 입력은 빈 문자열 반환")
    void maskingPhone_blank_returnsEmpty(String input) {
        assertEquals("", MaskingUtil.maskingPhone(input));
    }

    // 전화번호 마스킹: null은 빈 값으로 반환
    @Test
    @DisplayName("전화번호 마스킹: null 입력은 빈 문자열 반환")
    void maskingPhone_null_returnsEmpty() {
        assertEquals("", MaskingUtil.maskingPhone(null));
    }

    // 전화번호 마스킹: 마지막 4자리 마스킹 규칙 검증
    @ParameterizedTest
    @CsvSource({
            "01012345678, 0101234****",
            "010-1234-5678, 010-1234-****",
            "123, 123"
    })
    @DisplayName("전화번호 마스킹: 마지막 4자리 치환")
    void maskingPhone_replacesLast4Digits(String input, String expected) {
        assertEquals(expected, MaskingUtil.maskingPhone(input));
    }

    // 이메일 마스킹: 빈 문자열/공백은 빈 값으로 반환
    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    @DisplayName("이메일 마스킹: 공백 입력은 빈 문자열 반환")
    void maskEmail_blank_returnsEmpty(String input) {
        assertEquals("", MaskingUtil.maskEmail(input));
    }

    // 이메일 마스킹: null은 빈 값으로 반환
    @Test
    @DisplayName("이메일 마스킹: null 입력은 빈 문자열 반환")
    void maskEmail_null_returnsEmpty() {
        assertEquals("", MaskingUtil.maskEmail(null));
    }

    // 이메일 마스킹: 로컬 파트 마스킹 및 trim 동작 검증
    @ParameterizedTest
    @CsvSource({
            "ab@ex.com, ab@ex.com",
            "abcd@ex.com, ab**@ex.com",
            "'  abcd@ex.com  ', ab**@ex.com"
    })
    @DisplayName("이메일 마스킹: 로컬 파트 마스킹 및 trim 적용")
    void maskEmail_masksLocalPart(String input, String expected) {
        assertEquals(expected, MaskingUtil.maskEmail(input));
    }

    // 이메일 마스킹: 형식이 잘못된 입력은 예외 발생
    @ParameterizedTest
    @ValueSource(strings = { "a@ex.com", "@ex.com", "no-at-symbol" })
    @DisplayName("이메일 마스킹: 잘못된 입력은 예외 발생")
    void maskEmail_invalid_throws(String input) {
        assertThrows(StringIndexOutOfBoundsException.class, () -> MaskingUtil.maskEmail(input));
    }
}
