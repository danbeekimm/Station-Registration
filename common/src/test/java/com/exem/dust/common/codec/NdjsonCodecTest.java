package com.exem.dust.common.codec;

import com.exem.dust.common.protocol.Ack;
import com.exem.dust.common.protocol.Bye;
import com.exem.dust.common.protocol.Data;
import com.exem.dust.common.protocol.Hello;
import com.exem.dust.common.protocol.Message;
import com.exem.dust.common.protocol.Reject;
import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.common.protocol.Welcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NdjsonCodecTest {

    private final NdjsonCodec codec = new NdjsonCodec();

    static Stream<Message> allMessageTypes() {
        return Stream.of(
                new Hello("111311", "노원구", "secret-token", 1, 0),
                new Welcome(17),
                new Reject(RejectReason.OUT_OF_ORDER, "seq 18 expected"),
                new Data("111311", 17, LocalDateTime.of(2023, 3, 1, 16, 0), 162.0, null, null),
                new Ack(17),
                new Bye("111311"));
    }

    @ParameterizedTest
    @MethodSource("allMessageTypes")
    void roundTripsEveryMessageType(Message original) {
        var line = codec.encode(original);

        assertThat(line).doesNotContain("\n");
        assertThat(codec.decode(line)).isEqualTo(new DecodeResult.Decoded(original));
    }

    @Test
    void encodesTypeDiscriminatorAndNullConcentration() {
        var line = codec.encode(new Data("111311", 1, LocalDateTime.of(2023, 3, 1, 0, 0), null, 11.0, null));

        assertThat(line).contains("\"type\":\"DATA\"").contains("\"pm10\":null").contains("\"pm25\":11.0");
    }

    @Test
    void decodesHandWrittenDataLine() {
        var line = "{\"type\":\"DATA\",\"stationId\":\"111311\",\"seq\":17,\"measuredAt\":\"2023-03-01T16:00:00\",\"pm10\":162,\"pm25\":null}";

        assertThat(codec.decode(line)).isEqualTo(new DecodeResult.Decoded(
                new Data("111311", 17, LocalDateTime.of(2023, 3, 1, 16, 0), 162.0, null, null)));
    }

    @Test
    void brokenJsonIsMalformed() {
        assertThat(codec.decode("{\"type\":\"DATA\",")).isInstanceOf(DecodeResult.Invalid.class)
                .extracting("reason").isEqualTo(RejectReason.MALFORMED);
    }

    @Test
    void unknownTypeIsSchemaViolation() {
        assertThat(codec.decode("{\"type\":\"PING\"}")).isInstanceOf(DecodeResult.Invalid.class)
                .extracting("reason").isEqualTo(RejectReason.SCHEMA);
    }

    @Test
    void wrongFieldTypeIsSchemaViolation() {
        assertThat(codec.decode("{\"type\":\"ACK\",\"seq\":\"seventeen\"}")).isInstanceOf(DecodeResult.Invalid.class)
                .extracting("reason").isEqualTo(RejectReason.SCHEMA);
    }

    @Test
    void helloToStringHidesToken() {
        assertThat(new Hello("111311", "노원구", "secret-token", 1, 0).toString()).doesNotContain("secret-token");
    }
}
