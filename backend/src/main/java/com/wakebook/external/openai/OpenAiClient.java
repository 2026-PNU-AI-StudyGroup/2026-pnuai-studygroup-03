package com.wakebook.external.openai;

public interface OpenAiClient {

    /**
     * systemPrompt/userPrompt로 OpenAI Chat Completions API를 JSON 모드로 호출하고,
     * 응답 메시지의 raw JSON 문자열을 그대로 반환한다. 호출자가 자신의 DTO로 파싱한다.
     */
    String complete(String systemPrompt, String userPrompt);
}
