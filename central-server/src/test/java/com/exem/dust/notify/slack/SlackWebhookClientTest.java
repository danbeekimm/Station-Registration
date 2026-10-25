package com.exem.dust.notify.slack;

import com.exem.dust.notify.slack.dto.SendFailed;
import com.exem.dust.notify.slack.dto.SendRateLimited;
import com.exem.dust.notify.slack.dto.SendSucceeded;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** HTTP 응답이 SendOutcome으로 어떻게 번역되는지 Slack 없이 확인한다. */
class SlackWebhookClientTest {

    private static final String URL = "https://hooks.slack.test/services/T/B/X";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer slack = MockRestServiceServer.bindTo(builder).build();
    private final SlackWebhookClient client = new SlackWebhookClient(builder.build());

    @Test
    void okResponseIsSent() {
        slack.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"text\":\"hi\"}"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(client.send(URL, "{\"text\":\"hi\"}")).isEqualTo(new SendSucceeded());
        slack.verify();
    }

    @Test
    void tooManyRequestsHonoursRetryAfterHeader() {
        var headers = new HttpHeaders();
        headers.add("Retry-After", "7");
        slack.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThat(client.send(URL, "{}")).isEqualTo(new SendRateLimited(Duration.ofSeconds(7)));
    }

    @Test
    void tooManyRequestsWithoutHeaderFallsBackToDefault() {
        slack.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(client.send(URL, "{}")).isEqualTo(new SendRateLimited(Duration.ofSeconds(5)));
    }

    @Test
    void serverErrorIsFailedWithStatusAndBodyPreview() {
        slack.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("internal\n  error"));

        assertThat(client.send(URL, "{}")).isEqualTo(new SendFailed("HTTP 500 internal error"));
    }

    @Test
    void clientErrorIsFailed() {
        slack.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("no_service"));

        assertThat(client.send(URL, "{}")).isInstanceOfSatisfying(SendFailed.class,
                failed -> assertThat(failed.error()).startsWith("HTTP 404"));
    }

    @Test
    void connectionFailureIsFailedNotThrown() {
        var realClient = new SlackWebhookClient(RestClient.builder().build());

        assertThat(realClient.send("http://127.0.0.1:1/unreachable", "{}")).isInstanceOf(SendFailed.class);
    }
}
