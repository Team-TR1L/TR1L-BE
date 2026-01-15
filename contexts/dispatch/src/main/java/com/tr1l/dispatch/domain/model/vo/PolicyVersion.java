package com.tr1l.dispatch.domain.model.vo;

public record PolicyVersion(
        Integer value
) {
    public PolicyVersion next(){
        return new PolicyVersion(this.value() + 1);
    }
}
