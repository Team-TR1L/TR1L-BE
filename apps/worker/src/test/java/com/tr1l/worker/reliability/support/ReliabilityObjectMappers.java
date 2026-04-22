package com.tr1l.worker.reliability.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// object mapper 설정 집약
public final class ReliabilityObjectMappers {
    private static final ObjectMapper JSON = buildJsonMapper();
    private static final ObjectMapper YAML = buildYamlMapper();

    private ReliabilityObjectMappers() {
    }

    public static ObjectMapper json() {
        return JSON;
    }

    public static ObjectMapper yaml() {
        return YAML;
    }

    private static ObjectMapper buildJsonMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    private static ObjectMapper buildYamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
