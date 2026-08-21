package com.wakebook.trend.config;

import com.wakebook.external.openai.OpenAiChatClient;
import com.wakebook.external.openai.OpenAiClient;
import com.wakebook.external.openai.OpenAiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 트렌드 적격성/책 매칭 판정은 gpt-4o-mini로는 카테고리 분류가 실행마다 오락가락한다(연예인 개인사·
 * 스포츠 결과가 ELIGIBLE로 잘못 통과했다가 다음 실행엔 걸러지는 식). 이 판정에만 더 큰 모델
 * (openai.trend-model)을 쓰고, 다른 AI 호출(추천 이유 생성 등)은 기존 모델을 그대로 쓴다.
 */
@Configuration
public class TrendAiConfig {

    @Bean
    @Qualifier("trendOpenAiClient")
    public OpenAiClient trendOpenAiClient(OpenAiProperties properties) {
        return new OpenAiChatClient(
            new OpenAiProperties(properties.baseUrl(), properties.apiKey(), properties.trendModel(), properties.trendModel())
        );
    }
}
