package com.tr1l.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * ==========================
 * MaskingUtil
 *
 * 개인정보 마스킹 유틸리티입니다.
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */



public final class MaskingUtil {

    private MaskingUtil(){}

    /**
 * ==========================
 * MaskingUtil
 *
 * 개인정보 마스킹 유틸리티입니다.
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */


    public static String maskingName(String name){
        if( name == null) return "";
        String s = name.trim();
        if(s.isEmpty()) return "";

        int length = s.length();

        //첫글자
        if(length==1) return name;

        //두글자
        if(length==2) return name.charAt(0)+"*";

        //세글자 이상
        StringBuilder sb = new StringBuilder(length);
        sb.append(s.charAt(0));
        for (int i = 1; i < length - 1; i++) sb.append('*');
        sb.append(s.charAt(length - 1));
        return sb.toString();

    }

    /**
 * ==========================
 * MaskingUtil
 *
 * 개인정보 마스킹 유틸리티입니다.
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */
    public static String maskingPhone(String phone){
        if(phone == null) return "";
        String s = phone.trim();
        if(s.isEmpty()) return "";

        Pattern p = Pattern.compile("(\\d{0,})(\\d{4})$"); // 뒤 4자리 숫자

        // 마지막 4자리 *자리로 교체
        return s.replaceAll("(\\d{4})$", "****");
    }

    /**
 * ==========================
 * MaskingUtil
 *
 * 개인정보 마스킹 유틸리티입니다.
 *
 * @author nonstop
 * @version 1.0.0
 * @since 2026-01-22
 * ==========================
 */

    public static String maskEmail(String email) {
        if (email == null) return "";
        String s = email.trim();
        if (s.isEmpty()) return "";

        int at = s.indexOf('@');

        String local = s.substring(0, at);
        String domain = s.substring(at);
        String maskedLocal = local.substring(0, 2) + "*".repeat(local.length() - 2);
        return maskedLocal + domain;
    }

}


