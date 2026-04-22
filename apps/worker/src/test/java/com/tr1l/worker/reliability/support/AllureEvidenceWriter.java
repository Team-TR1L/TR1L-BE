package com.tr1l.worker.reliability.support;

import io.qameta.allure.Allure;

// Allure 근거 파일 부착기
public final class AllureEvidenceWriter {

    public void attachJson(String name, Object value) {
        try {
            Allure.addAttachment(name, "application/json", ReliabilityObjectMappers.json().writeValueAsString(value), ".json");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to attach JSON evidence: " + name, e);
        }
    }

    public void attachText(String name, String value) {
        Allure.addAttachment(name, "text/plain", value, ".txt");
    }
}
