package cn.pianke.controller;

import cn.pianke.common.Result;
import cn.pianke.service.DesktopWindowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@Profile("local")
@RequestMapping("/api/desktop")
public class DesktopWindowController {
    private final DesktopWindowService windows;
    public DesktopWindowController(DesktopWindowService windows) { this.windows = windows; }
    private void guard(HttpServletRequest request) { windows.requireLocal(request.getRemoteAddr(), request.getHeader("Origin")); }
    @GetMapping public Result<?> capabilities(HttpServletRequest request) { guard(request); return Result.success(windows.capabilities()); }
    @PostMapping("/windows") public Result<?> open(@RequestBody Open request, HttpServletRequest caller) {
        guard(caller); return Result.success(windows.open(request.path() == null ? "/" : request.path()));
    }
    @GetMapping("/windows/{token}") public Result<?> get(@PathVariable String token, HttpServletRequest caller) {
        guard(caller); return Result.success(windows.get(token));
    }
    @PostMapping("/windows/{token}") public Result<?> update(@PathVariable String token, @RequestBody Settings settings, HttpServletRequest caller) {
        guard(caller); return Result.success(windows.update(token, settings.opacity(), settings.topmost(), settings.clickThrough(), settings.autoHide()));
    }
    @PostMapping("/windows/{token}/progress") public Result<?> progress(@PathVariable String token, @RequestBody DesktopWindowService.Progress progress, HttpServletRequest caller) {
        guard(caller); windows.progress(token, progress); return Result.success(Map.of());
    }
    @PostMapping("/windows/{token}/close") public Result<?> close(@PathVariable String token, HttpServletRequest caller) {
        guard(caller); return Result.success(windows.close(token));
    }
    @PostMapping("/windows/{token}/status") public Result<?> playbackStatus(@PathVariable String token, @RequestBody DesktopWindowService.PlaybackStatus status, HttpServletRequest caller) {
        guard(caller); windows.playbackStatus(token, status); return Result.success(Map.of());
    }
    public record Open(String path) {}
    @PostMapping("/windows/{token}/visibility") public Result<?> visibility(@PathVariable String token, @RequestBody Visibility visibility, HttpServletRequest caller) {
        guard(caller); return Result.success(windows.visibility(token, visibility.hidden()));
    }
    public record Visibility(boolean hidden) {}
    public record Settings(int opacity, boolean topmost, boolean clickThrough, boolean autoHide) {}
}
