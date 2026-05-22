package com.codesense.codesense.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!local")
public class OpenAiModelConfig {

    @Bean
    @Primary
    public ChatModel chatModel(@Qualifier("openAiChatModel") OpenAiChatModel model) {
        return model;
    }
}
