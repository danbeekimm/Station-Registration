package com.exem.dust.simulator.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatorOptionsTest {

    @Test
    void parsesBothArgumentStyles() {
        var options = SimulatorOptions.parse(new String[]{
                "--file", "data/노원구.json", "--server=10.0.0.5:9100", "--interval-ms", "10", "--token=abc"});

        assertThat(options.file()).isEqualTo(Path.of("data/노원구.json"));
        assertThat(options.server()).isEqualTo(new ServerEndpoint("10.0.0.5", 9100));
        assertThat(options.interval()).isEqualTo(Duration.ofMillis(10));
        assertThat(options.token()).isEqualTo("abc");
        assertThat(options.ackTimeout()).isEqualTo(Duration.ofMillis(3000));
    }

    @Test
    void requiresFileUnlessHelp() {
        assertThatThrownBy(() -> SimulatorOptions.parse(new String[]{"--interval-ms", "10"}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("--file");
        assertThat(SimulatorOptions.parse(new String[]{"--help"}).help()).isTrue();
    }

    @Test
    void rejectsUnknownOptionAndBadNumbers() {
        assertThatThrownBy(() -> SimulatorOptions.parse(new String[]{"--file", "x", "--speed", "1"}))
                .hasMessageContaining("알 수 없는 옵션");
        assertThatThrownBy(() -> SimulatorOptions.parse(new String[]{"--file", "x", "--interval-ms", "0"}))
                .hasMessageContaining("1 이상");
        assertThatThrownBy(() -> SimulatorOptions.parse(new String[]{"--file", "x", "--server", "localhost"}))
                .hasMessageContaining("host:port");
        assertThatThrownBy(() -> SimulatorOptions.parse(new String[]{"--file"}))
                .hasMessageContaining("값이 없습니다");
    }

    @Test
    void resolvesTokenInPriorityOrder() {
        var explicit = SimulatorOptions.parse(new String[]{"--file", "x", "--token", "from-arg"});
        var fromEnv = SimulatorOptions.parse(new String[]{"--file", "x"});
        var environment = Map.of("STATION_TOKEN_111311", "per-station", "STATION_TOKEN", "shared");

        assertThat(explicit.resolveToken("111311", environment)).contains("from-arg");
        assertThat(fromEnv.resolveToken("111311", environment)).contains("per-station");
        assertThat(fromEnv.resolveToken("999999", environment)).contains("shared");
        assertThat(fromEnv.resolveToken("999999", Map.of("STATION_TOKEN", "  "))).isEmpty();
    }
}
