package com.careerpilot.config;

import java.time.Duration;
import com.openai.core.Timeout;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiTimeoutConfig {
    @Bean
    OpenAiHttpClientBuilderCustomizer openAiTimeouts() {
        Timeout timeout = Timeout.builder()
                .connect(Duration.ofSeconds(10))
                .read(Duration.ofSeconds(120))
                .write(Duration.ofSeconds(30))
                .request(Duration.ofSeconds(150))
                .build();
        return builder -> builder.timeout(timeout);
    }
}
