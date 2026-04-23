package com.tr1l.worker.reliability.ai;

// OpenAI 요청 실패 정보
public final class OpenAiApiRequestException extends IllegalStateException {
    private final String path;
    private final int statusCode;
    private final String requestBody;
    private final String responseBody;

    public OpenAiApiRequestException(String path, int statusCode, String requestBody, String responseBody) {
        super("OpenAI API request failed: path=" + path + " status=" + statusCode + " body=" + responseBody);
        this.path = path;
        this.statusCode = statusCode;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
    }

    public String path() {
        return path;
    }

    public int statusCode() {
        return statusCode;
    }

    public String requestBody() {
        return requestBody;
    }

    public String responseBody() {
        return responseBody;
    }
}
