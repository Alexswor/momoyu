package cn.pianke.service;

import cn.pianke.exception.BizException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DesktopWindowServiceTest {
    private final DesktopWindowService service = new DesktopWindowService("http://127.0.0.1:5173");
    @Test void allowsOnlyLocalApplicationRoutes() {
        assertThat(service.target("/watch/bf-123/2?popup=1&t=35&rate=1.5", "test").toString())
                .isEqualTo("http://127.0.0.1:5173/watch/bf-123/2?popup=1&t=35&rate=1.5&desktopWindow=test");
        for (String path : new String[]{"https://example.com", "//example.com", "/watch/../test", "/api/health", "/?desktopWindow=forged", "/?redirect=https://example.com"})
            assertThatThrownBy(() -> service.target(path, "test")).isInstanceOf(BizException.class);
    }
    @Test void rejectsRemoteClientsAndUntrustedOrigins() {
        assertThatCode(() -> service.requireLocal("127.0.0.1", "http://127.0.0.1:5173")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireLocal("192.168.1.8", "http://127.0.0.1:5173")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.requireLocal("127.0.0.1", "https://example.com")).isInstanceOf(BizException.class);
    }
    @Test void doesNotAcceptArbitraryWindowHandlesOrInvalidProgress() {
        assertThatThrownBy(() -> service.get("12345")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.progress("test", new DesktopWindowService.Progress("bf-1", 1, Double.NaN, 1, false))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.update("test", 0, true, false, false)).isInstanceOf(BizException.class);
    }
}
