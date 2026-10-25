package com.exem.dust.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/** Slack Webhook 호출용 RestClient. 외부 지연이 디스패처를 오래 잡지 않도록 연결·응답 타임아웃을 짧게 둔다. */
@Configuration
public class SlackConfig {

    @Bean
    public RestClient slackRestClient(RestClient.Builder builder, DustProperties properties) {
        var slack = properties.slack();
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(slack.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(slack.readTimeout());
        return builder.requestFactory(requestFactory).build();
    }
}
