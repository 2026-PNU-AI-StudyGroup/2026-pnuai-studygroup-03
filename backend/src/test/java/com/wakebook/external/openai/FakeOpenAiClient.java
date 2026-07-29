package com.wakebook.external.openai;

public class FakeOpenAiClient implements OpenAiClient {

    private String lastSystemPrompt;
    private String lastUserPrompt;
    private String response = "{}";

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        this.lastSystemPrompt = systemPrompt;
        this.lastUserPrompt = userPrompt;
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String lastSystemPrompt() {
        return lastSystemPrompt;
    }

    public String lastUserPrompt() {
        return lastUserPrompt;
    }
}
