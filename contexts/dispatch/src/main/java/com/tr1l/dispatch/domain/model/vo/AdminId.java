package com.tr1l.dispatch.domain.model.vo;

public record AdminId(
        Long value
) {
    public static AdminId of(Long value) {
        return new AdminId(value);
    }
}
