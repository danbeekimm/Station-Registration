package com.exem.dust.notify.slack;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Slack Incoming Webhook에 payload를 POST한다. 응답 상태를 예외가 아닌 {@link SendOutcome}으로 돌려준다. */
@Component
public class SlackWebhookClient {

    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(5);
    private static final int BODY_PREVIEW_LIMIT = 200;

    private final RestClient restClient;

    public SlackWebhookClient(RestClient slackRestClient) {
        this.restClient = slackRestClient;
    }

    public SendOutcome send(String webhookUrl, String payloadJson) {
        try {
            return restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payloadJson)
                    .exchange((request, response) -> {
                        var status = response.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            return new SendOutcome.Sent();
                        }
                        if (status.value() == 429) {
                            return new SendOutcome.RateLimited(parseRetryAfter(response.getHeaders().getFirst("Retry-After")));
                        }
                        return new SendOutcome.Failed("HTTP %d %s".formatted(status.value(), bodyPreview(response.getBody())));
                    });
        } catch (RestClientException e) {
            // 연결 실패·타임아웃. 메시지에 URL이 섞일 수 있어 예외 종류와 원인만 남긴다
            var cause = e.getMostSpecificCause();
            return new SendOutcome.Failed(cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    private static Duration parseRetryAfter(String header) {
        if (header == null) {
            return DEFAULT_RETRY_AFTER;
        }
        try {
            return Duration.ofSeconds(Math.max(1, Long.parseLong(header.strip())));
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_AFTER;
        }
    }

    private static String bodyPreview(java.io.InputStream body) {
        try {
            var text = new String(body.readNBytes(BODY_PREVIEW_LIMIT), StandardCharsets.UTF_8);
            return text.replaceAll("\\s+", " ").strip();
        } catch (IOException e) {
            return "(본문 읽기 실패)";
        }
    }
}
