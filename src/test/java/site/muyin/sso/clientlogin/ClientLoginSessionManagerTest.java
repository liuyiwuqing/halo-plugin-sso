package site.muyin.sso.clientlogin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import site.muyin.sso.oauth.Pkce;

class ClientLoginSessionManagerTest {

    @Test
    void createsPkceLoginSessionAndConsumesStateOnce() {
        var manager = new ClientLoginSessionManager();

        var session = manager.start("/posts/1");

        assertThat(session.state()).isNotBlank();
        assertThat(session.codeVerifier()).isNotBlank();
        assertThat(Pkce.matchesS256(session.codeVerifier(), session.codeChallenge())).isTrue();
        assertThat(session.returnUrl()).isEqualTo("/posts/1");

        var consumed = manager.consume(session.state());
        assertThat(consumed.returnUrl()).isEqualTo("/posts/1");

        assertThatThrownBy(() -> manager.consume(session.state()))
            .isInstanceOf(ClientLoginException.class)
            .hasMessageContaining("已使用");
    }

    @Test
    void removesConsumedLoginSessionBeforeCheckingCapacity() {
        var manager = new ClientLoginSessionManager(
            fixedClock(),
            Duration.ofMinutes(10),
            new SecureRandom(),
            1
        );
        var firstSession = manager.start("/posts/1");

        manager.consume(firstSession.state());

        var secondSession = manager.start("/posts/2");
        assertThat(secondSession.state()).isNotBlank();
    }

    @Test
    void removesExpiredLoginSessionBeforeCheckingCapacity() {
        var clock = new MutableClock(Instant.parse("2026-06-24T08:00:00Z"));
        var manager = new ClientLoginSessionManager(
            clock,
            Duration.ofMinutes(10),
            new SecureRandom(),
            1
        );

        manager.start("/posts/1");
        clock.advance(Duration.ofMinutes(10));

        var replacementSession = manager.start("/posts/2");
        assertThat(replacementSession.state()).isNotBlank();
    }

    @Test
    void rejectsNewLoginSessionWhenCapacityIsFull() {
        var manager = new ClientLoginSessionManager(
            fixedClock(),
            Duration.ofMinutes(10),
            new SecureRandom(),
            1
        );

        manager.start("/posts/1");

        assertThatThrownBy(() -> manager.start("/posts/2"))
            .isInstanceOf(ClientLoginException.class)
            .hasMessageContaining("上限");
    }

    @Test
    void limitsOutstandingSessionsPerRequesterWithoutBlockingOtherRequesters() {
        var manager = new ClientLoginSessionManager(
            fixedClock(),
            Duration.ofMinutes(10),
            new SecureRandom(),
            10,
            1
        );

        var first = manager.start("/posts/1", "192.0.2.10");

        assertThatThrownBy(() -> manager.start("/posts/2", "192.0.2.10"))
            .isInstanceOf(ClientLoginException.class)
            .hasMessageContaining("请求来源");
        assertThat(manager.start("/posts/3", "192.0.2.11").state()).isNotBlank();

        manager.consume(first.state());
        assertThat(manager.start("/posts/4", "192.0.2.10").state()).isNotBlank();
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-24T08:00:00Z"), ZoneId.of("UTC"));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
