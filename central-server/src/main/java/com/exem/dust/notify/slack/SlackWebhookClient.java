package com.exem.dust.notify.slack;

import com.exem.dust.notify.slack.dto.SendFailed;
import com.exem.dust.notify.slack.dto.SendOutcome;
import com.exem.dust.notify.slack.dto.SendRateLimited;
import com.exem.dust.notify.slack.dto.SendSucceeded;
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

    /** payload를 POST하고 응답 상태를 분류한다. 예외도 Failed로 바꿔 디스패처가 상태 전이만 신경 쓰게 한다. */
    public SendOutcome send(String webhookUrl, String payloadJson) {
        try {
            return restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payloadJson)
                    .exchange((request, response) -> {
                        var status = response.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            return new SendSucceeded();
                        }
                        if (status.value() == 429) {
                            return new SendRateLimited(parseRetryAfter(response.getHeaders().getFirst("Retry-After")));
                        }
                        return new SendFailed("HTTP %d %s".formatted(status.value(), bodyPreview(response.getBody())));
                    });
        } catch (RestClientException e) {
            // 연결 실패·타임아웃. 메시지에 URL이 섞일 수 있어 예외 종류와 원인만 남긴다
            var cause = e.getMostSpecificCause();
            return new SendFailed(cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    /** Retry-After 헤더(초). 없거나 숫자가 아니면 기본 5초, 0 이하는 1초로 올린다. */
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

    /** 오류 본문 앞부분만 공백 정리해 한 줄로. slack_error 컬럼에 남는다. */
    private static String bodyPreview(java.io.InputStream body) {
        try {
            var text = new String(body.readNBytes(BODY_PREVIEW_LIMIT), StandardCharsets.UTF_8);
            return text.replaceAll("\\s+", " ").strip();
        } catch (IOException e) {
            return "(본문 읽기 실패)";
        }
    }
}
