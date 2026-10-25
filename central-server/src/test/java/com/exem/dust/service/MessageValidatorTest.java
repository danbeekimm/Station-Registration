package com.exem.dust.service;

import com.exem.dust.common.protocol.Data;
import com.exem.dust.common.protocol.Reject;
import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.config.DustProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MessageValidatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 12, 0);
    private static final LocalDateTime LAST = LocalDateTime.of(2023, 3, 1, 5, 0);

    private final MessageValidator validator = new MessageValidator(
            new DustProperties(null, new DustProperties.Validation(2000, 1.0, Duration.ofDays(1), 3), null, List.of()),
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private final MessageValidator.SessionContext context =
            new MessageValidator.SessionContext("111311", 5, Optional.of(LAST));

    @Test
    void acceptsNextSeqAndResendOfLastSeq() {
        assertThat(validator.validate(data(6, LAST.plusHours(1), 50.0, 20.0, null), context)).isEmpty();
        assertThat(validator.validate(data(5, LAST, 50.0, 20.0, null), context)).isEmpty();
    }

    @Test
    void rejectsMissingFieldsAndForeignStation() {
        assertThat(reasonOf(new Data(null, 6, LAST.plusHours(1), 1.0, 1.0, null))).isEqualTo(RejectReason.SCHEMA);
        assertThat(reasonOf(new Data("111311", 0, LAST.plusHours(1), 1.0, 1.0, null))).isEqualTo(RejectReason.SCHEMA);
        assertThat(reasonOf(new Data("111311", 6, null, 1.0, 1.0, null))).isEqualTo(RejectReason.SCHEMA);
        assertThat(reasonOf(new Data("111121", 6, LAST.plusHours(1), 1.0, 1.0, null))).isEqualTo(RejectReason.FORBIDDEN);
    }

    @Test
    void rejectsSkippedAndStaleSeq() {
        assertThat(reasonOf(data(8, LAST.plusHours(1), 1.0, 1.0, null))).isEqualTo(RejectReason.OUT_OF_ORDER);
        assertThat(reasonOf(data(4, LAST.plusHours(1), 1.0, 1.0, null))).isEqualTo(RejectReason.OUT_OF_ORDER);
    }

    @Test
    void rejectsOffHourPastAndFarFutureTimes() {
        assertThat(reasonOf(data(6, LAST.plusMinutes(90), 1.0, 1.0, null))).isEqualTo(RejectReason.BAD_TIME);
        assertThat(reasonOf(data(6, LAST.minusHours(1), 1.0, 1.0, null))).isEqualTo(RejectReason.BAD_TIME);
        assertThat(reasonOf(data(6, NOW.plusDays(2), 1.0, 1.0, null))).isEqualTo(RejectReason.BAD_TIME);
        assertThat(validator.validate(data(6, LAST.plusDays(3), 1.0, 1.0, null), context)).isEmpty();
    }

    @Test
    void rejectsConcentrationsOutsideLimitsButAllowsNull() {
        assertThat(reasonOf(data(6, LAST.plusHours(1), 5000.0, 1.0, null))).isEqualTo(RejectReason.OUT_OF_RANGE);
        assertThat(reasonOf(data(6, LAST.plusHours(1), 1.0, -0.1, null))).isEqualTo(RejectReason.OUT_OF_RANGE);
        assertThat(reasonOf(data(6, LAST.plusHours(1), 1.0, 1.0, 1.5))).isEqualTo(RejectReason.OUT_OF_RANGE);
        assertThat(reasonOf(data(6, LAST.plusHours(1), Double.NaN, 1.0, null))).isEqualTo(RejectReason.OUT_OF_RANGE);
        assertThat(validator.validate(data(6, LAST.plusHours(1), null, null, null), context)).isEmpty();
        assertThat(validator.validate(data(6, LAST.plusHours(1), 0.0, 2000.0, 1.0), context)).isEmpty();
    }

    private RejectReason reasonOf(Data data) {
        return validator.validate(data, context).map(Reject::reason).orElseThrow();
    }

    private static Data data(int seq, LocalDateTime measuredAt, Double pm10, Double pm25, Double o3) {
        return new Data("111311", seq, measuredAt, pm10, pm25, o3);
    }
}
