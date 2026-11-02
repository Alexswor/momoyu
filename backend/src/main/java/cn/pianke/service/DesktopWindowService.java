package cn.pianke.service;

import cn.pianke.exception.BizException;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser.MSG;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Local Windows integration; never accepts caller-supplied HWNDs or commands. */
@Service
@Profile("local")
public class DesktopWindowService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DesktopWindowService.class);
    private final URI origin;
    @Value("${app.desktop.direct-network:true}")
    private boolean directNetwork = true;
    private final Path records = Path.of(System.getProperty("user.dir"), "data", "desktop-windows");
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scanner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "momoyu-window-scan"); t.setDaemon(true); return t;
    });
    private volatile Thread hotkeys;
    private volatile String toggleKey = "";
    private volatile boolean started;
    private static final int EXSTYLE = -20, LAYERED = 0x80000, TRANSPARENT = 0x20;
    private static final Set<String> QUERY_KEYS = Set.of("source", "q", "category", "sort", "popup", "t", "rate", "paused");

    public DesktopWindowService(@Value("${app.desktop.origin:http://127.0.0.1:5173}") String origin) {
        this.origin = URI.create(origin);
        if (!"http".equals(this.origin.getScheme()) || !Set.of("localhost", "127.0.0.1", "[::1]").contains(this.origin.getHost()))
            throw new IllegalArgumentException("Desktop origin must be a local HTTP origin");
    }

    public void requireLocal(String address, String requestOrigin) {
        if (!Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(address)
                || requestOrigin != null && !origin.toString().equals(requestOrigin))
            throw new BizException(HttpStatus.FORBIDDEN, "窗口控制仅供本机页面使用");
    }

    public Map<String, Object> capabilities() {
        return Map.of("available", System.getProperty("os.name", "").startsWith("Windows") && browser() != null);
    }

    URI target(String path, String token) {
        try {
            URI relative = URI.create(path);
            if (path.length() > 2000 || relative.isAbsolute() || relative.getRawAuthority() != null || relative.getFragment() != null
                    || !relative.getPath().matches("/(watch/[a-zA-Z0-9_-]+(/[1-9][0-9]{0,4})?|library|saved|history)?")) throw new IllegalArgumentException();
            if (relative.getRawQuery() != null) for (String item : relative.getRawQuery().split("&")) {
                if (!QUERY_KEYS.contains(URLDecoder.decode(item.split("=", 2)[0], StandardCharsets.UTF_8))) throw new IllegalArgumentException();
            }
            return origin.resolve(path + (relative.getRawQuery() == null ? "?" : "&") + "desktopWindow=" + token);
        } catch (IllegalArgumentException e) { throw new BizException(HttpStatus.BAD_REQUEST, "只能打开本站页面"); }
    }

    public synchronized Map<String, Object> open(String path) {
        if (!Boolean.TRUE.equals(capabilities().get("available"))) throw new BizException(HttpStatus.CONFLICT, "此功能需要本机 Windows 和 Edge 或 Chrome");
        windows.values().removeIf(w -> w.closed && System.currentTimeMillis() - w.created > 600_000);
        if (windows.values().stream().filter(w -> !w.closed).count() >= 3) throw new BizException(HttpStatus.CONFLICT, "请先关闭不用的小窗");
        String token = UUID.randomUUID().toString();
        URI url = target(path, token);
        try {
            Path executable = browser();
            Path profile = Path.of(System.getProperty("user.dir"), "data", "desktop-" + executable.getFileName().toString().replace(".exe", "") + (directNetwork ? "-direct" : "-system")).toAbsolutePath();
            Files.createDirectories(profile);
            List<String> command = new ArrayList<>(List.of(executable.toString(), "--app=" + url, "--user-data-dir=" + profile,
                    "--no-first-run", "--no-default-browser-check", "--disable-background-mode", "--window-size=460,720", "--autoplay-policy=no-user-gesture-required"));
            if (directNetwork) command.add("--no-proxy-server");
            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.appendTo(profile.resolve("browser.log").toFile())).start();
            Window w = new Window(token);
            windows.put(token, w);
            Files.createDirectories(records);
            Files.writeString(records.resolve(token + ".window"), token);
            startMonitor();
            return state(w);
        } catch (java.io.IOException e) { throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "小窗启动失败，请稍后重试"); }
    }

    private Path browser() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return null;
        for (String tail : new String[]{"Google/Chrome/Application/chrome.exe", "Microsoft/Edge/Application/msedge.exe"}) {
            for (String base : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), System.getenv("LOCALAPPDATA")}) {
                if (base == null) continue;
                Path p = Path.of(base, tail); if (Files.isRegularFile(p)) return p;
            }
        }
        return null;
    }

    private Window find(String token) {
        Window w = windows.get(token);
        if (w == null && token.matches("[a-f0-9-]{36}") && Files.isRegularFile(records.resolve(token + ".window"))) {
            w = windows.computeIfAbsent(token, id -> { Window restored = new Window(id); restored.opacity = 100; restored.topmost = false; return restored; });
            startMonitor();
        }
        if (w == null) throw new BizException(HttpStatus.NOT_FOUND, "小窗已失效，请重新打开");
        return w;
    }
    private synchronized void startMonitor() {
        if (hotkeys == null || !hotkeys.isAlive()) startHotkeys();
        if (!started) { started = true; scanner.scheduleWithFixedDelay(this::scan, 0, 200, TimeUnit.MILLISECONDS); }
    }
    public Map<String, Object> get(String token) { return state(find(token)); }
    private Map<String, Object> state(Window w) {
        synchronized (w) { return snapshot(w); }
    }
    private Map<String, Object> snapshot(Window w) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", w.token); result.put("ready", w.handle != null); result.put("closed", w.closed);
        result.put("identityReady", w.handle != null);
        result.put("opacity", w.opacity); result.put("topmost", w.topmost); result.put("clickThrough", w.clickThrough);
        result.put("autoHide", w.autoHide); result.put("toggleKey", toggleKey); result.put("hidden", w.hidden); result.put("error", w.error);
        result.put("progress", w.progress);
        result.put("playbackStatus", w.playbackStatus);
        if (w.handle != null && !w.closed && User32.INSTANCE.IsWindow(w.handle)) {
            com.sun.jna.ptr.ByteByReference alpha = new com.sun.jna.ptr.ByteByReference();
            if (User32.INSTANCE.GetLayeredWindowAttributes(w.handle, new com.sun.jna.ptr.IntByReference(), alpha, new com.sun.jna.ptr.IntByReference()))
                result.put("appliedOpacity", Math.round(Byte.toUnsignedInt(alpha.getValue()) * 100.0 / 255));
            int styles = User32.INSTANCE.GetWindowLong(w.handle, EXSTYLE);
            result.put("appliedTopmost", (styles & 8) != 0);
            result.put("appliedClickThrough", (styles & TRANSPARENT) != 0);
            result.put("visible", User32.INSTANCE.IsWindowVisible(w.handle));
            char[] title = new char[256];
            User32.INSTANCE.GetWindowText(w.handle, title, title.length);
            result.put("title", Native.toString(title));
            result.put("resizable", (User32.INSTANCE.GetWindowLong(w.handle, -16) & 0x40000) != 0);
        }
        return result;
    }

    public Map<String, Object> update(String token, int opacity, boolean topmost, boolean clickThrough, boolean autoHide) {
        Window w = find(token);
        if (opacity < 20 || opacity > 100) throw new BizException(HttpStatus.BAD_REQUEST, "不透明度范围为20%至100%");
        if ((clickThrough || autoHide) && toggleKey.isEmpty()) throw new BizException(HttpStatus.CONFLICT, "隐藏快捷键被占用，暂不能开启穿透或移入隐藏");
        synchronized (w) {
            if (w.closed || w.handle == null) throw new BizException(HttpStatus.CONFLICT, "小窗尚未就绪");
            apply(w, opacity, topmost, clickThrough);
            w.opacity = opacity; w.topmost = topmost; w.clickThrough = clickThrough; w.autoHide = autoHide;
            if (!autoHide && !w.hidden) User32.INSTANCE.ShowWindow(w.handle, 4);
            return state(w);
        }
    }

    public void progress(String token, Progress progress) {
        if (progress == null || progress.id == null || !progress.id.matches("[a-zA-Z0-9_-]{1,100}") || progress.episode < 1 || progress.episode > 99999
                || !Double.isFinite(progress.time) || progress.time < 0 || progress.time > 864000
                || !Set.of(.75, 1., 1.25, 1.5, 2.).contains(progress.rate)) throw new BizException(HttpStatus.BAD_REQUEST, "播放进度不正确");
        Window w = find(token);
        if (!(w.closed && w.progress != null && w.progress.resume())) w.progress = progress;
    }
    public void playbackStatus(String token, PlaybackStatus status) {
        if (status == null || !Double.isFinite(status.time()) || status.time() < 0 || status.readyState() < -1 || status.readyState() > 4
                || status.error() == null || status.error().length() > 500) throw new BizException(HttpStatus.BAD_REQUEST, "播放器状态不正确");
        find(token).playbackStatus = status;
    }
    public Map<String, Object> close(String token) {
        Window w = find(token);
        synchronized (w) {
            if (w.handle != null && User32.INSTANCE.IsWindow(w.handle)) {
                try { restore(w); } finally {
                    WindowProperties.INSTANCE.RemovePropW(w.handle, identity(w));
                    User32.INSTANCE.PostMessage(w.handle, 0x10, null, null);
                }
            }
            w.closed = true;
            try { Files.deleteIfExists(records.resolve(token + ".window")); } catch (java.io.IOException e) { LOG.debug("小窗记录清理失败", e); }
        }
        return state(w);
    }

    private void apply(Window w, int opacity, boolean topmost, boolean clickThrough) {
        User32 u = User32.INSTANCE;
        int styles = u.GetWindowLong(w.handle, EXSTYLE) | LAYERED;
        u.SetWindowLong(w.handle, EXSTYLE, clickThrough ? styles | TRANSPARENT : styles & ~TRANSPARENT);
        if (!u.SetLayeredWindowAttributes(w.handle, 0, (byte) Math.round(opacity * 255.0 / 100), 2))
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "Windows 未能应用透明度：" + Native.getLastError());
        // HWND_TOPMOST/NOTOPMOST are signed pointer-sized pseudo-handles on Win64.
        if (!u.SetWindowPos(w.handle, new HWND(Pointer.createConstant(topmost ? -1L : -2L)), 0, 0, 0, 0, 0x13))
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "Windows 未能应用置顶：" + Native.getLastError());
    }
    private void restore(Window w) {
        apply(w, 100, false, false);
        w.opacity = 100; w.topmost = false; w.clickThrough = false; w.autoHide = false; w.hidden = false;
        User32.INSTANCE.ShowWindow(w.handle, 4);
    }
    public Map<String, Object> visibility(String token, boolean hidden) {
        Window w = find(token);
        synchronized (w) {
            if (hidden && toggleKey.isEmpty()) throw new BizException(HttpStatus.CONFLICT, "隐藏快捷键被占用，暂不能隐藏小窗");
            if (w.closed || w.handle == null) throw new BizException(HttpStatus.CONFLICT, "小窗尚未就绪");
            setHidden(w, hidden);
            return state(w);
        }
    }
    private void setHidden(Window w, boolean hidden) {
        w.hidden = hidden;
        User32.INSTANCE.ShowWindow(w.handle, hidden ? 0 : 4);
        if (!hidden) User32.INSTANCE.SetForegroundWindow(w.handle);
    }
    private WString identity(Window w) { return new WString("Momoyu." + w.token); }
    private void prepareWindow(Window w) {
        User32 u = User32.INSTANCE;
        if (!WindowProperties.INSTANCE.SetPropW(w.handle, identity(w), Pointer.createConstant(1L)))
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "未能绑定小窗标识");
        int style = u.GetWindowLong(w.handle, -16);
        if ((style & 0x70000) != 0x70000) {
            u.SetWindowLong(w.handle, -16, style | 0x70000);
            u.SetWindowPos(w.handle, null, 0, 0, 0, 0, 0x37);
        }
        apply(w, w.opacity, w.topmost, false);
    }
    private void scan() {
        for (Window w : windows.values()) synchronized (w) {
            try {
                if (w.closed) continue;
                User32 u = User32.INSTANCE;
                if (w.handle == null) {
                    u.EnumWindows((handle, data) -> {
                        char[] title = new char[256], type = new char[128];
                        u.GetWindowText(handle, title, title.length); u.GetClassName(handle, type, type.length);
                        boolean owned = Native.toString(title).contains("[摸摸鱼-" + w.token + "]")
                                || WindowProperties.INSTANCE.GetPropW(handle, identity(w)) != null;
                        if (owned && Native.toString(type).equals("Chrome_WidgetWin_1")) {
                            w.handle = handle; return false;
                        }
                        return true;
                    }, null);
                    if (w.handle != null) prepareWindow(w);
                    else if (System.currentTimeMillis() - w.created > 30_000) { w.error = "未能连接小窗，请关闭后重新打开"; w.closed = true; }
                } else if (!u.IsWindow(w.handle)) w.closed = true;
                else {
                    if (w.hidden) { if (u.IsWindowVisible(w.handle)) u.ShowWindow(w.handle, 0); continue; }
                    if (((u.GetWindowLong(w.handle, EXSTYLE) & 8) != 0) != w.topmost) apply(w, w.opacity, w.topmost, w.clickThrough);
                    if (!w.autoHide) continue;
                    POINT point = new POINT(); RECT rect = new RECT();
                    if (u.GetCursorPos(point) && u.GetWindowRect(w.handle, rect)) {
                        boolean inside = point.x >= rect.left && point.x < rect.right && point.y >= rect.top && point.y < rect.bottom;
                        if (inside == u.IsWindowVisible(w.handle)) u.ShowWindow(w.handle, inside ? 0 : 4);
                    }
                }
            } catch (RuntimeException | LinkageError e) { LOG.warn("本机小窗连接失败", e); w.error = "窗口连接失败，请重新打开"; w.closed = true; }
        }
        boolean active = windows.values().stream().anyMatch(w -> !w.closed);
        if (active && (hotkeys == null || !hotkeys.isAlive())) startMonitor();
        else if (!active && hotkeys != null) hotkeys.interrupt();
    }
    private void startHotkeys() {
        hotkeys = new Thread(() -> {
            User32 u = User32.INSTANCE; MSG msg = new MSG();
            u.PeekMessage(msg, null, 0, 0, 0);
            int modifiers = 0x4001;
            if (u.RegisterHotKey(null, 0x4d59, modifiers, 0x5a)) toggleKey = "Alt+Z";
            else if (u.RegisterHotKey(null, 0x4d59, 0x4003, 0x5a)) toggleKey = "Ctrl+Alt+Z";
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    while (u.PeekMessage(msg, null, 0, 0, 1)) if (msg.message == 0x312) {
                        boolean hide = windows.values().stream().anyMatch(w -> !w.closed && w.handle != null && !w.hidden);
                        for (Window w : windows.values()) synchronized (w) {
                            if (!w.closed && w.handle != null) setHidden(w, hide);
                        }
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { u.UnregisterHotKey(null, 0x4d59); toggleKey = ""; }
        }, "momoyu-window-hotkey"); hotkeys.setDaemon(true); hotkeys.start();
    }
    @PreDestroy public void shutdown() {
        scanner.shutdownNow(); if (hotkeys != null) hotkeys.interrupt();
        for (Window w : windows.values()) synchronized (w) {
            try { if (w.handle != null && User32.INSTANCE.IsWindow(w.handle)) restore(w); }
            catch (RuntimeException e) { LOG.warn("小窗恢复失败", e); }
        }
    }
    public record Progress(String id, int episode, double time, double rate, boolean resume) {}
    public record PlaybackStatus(int readyState, int networkState, double time, String error) {}
    public interface WindowProperties extends StdCallLibrary {
        WindowProperties INSTANCE = Native.load("user32", WindowProperties.class);
        boolean SetPropW(HWND window, WString key, Pointer value);
        Pointer GetPropW(HWND window, WString key);
        Pointer RemovePropW(HWND window, WString key);
    }
    private static class Window {
        final String token; final long created = System.currentTimeMillis();
        volatile HWND handle; volatile boolean closed, clickThrough, autoHide, hidden;
        volatile int opacity = 50; volatile boolean topmost = true;
        volatile String error = ""; volatile Progress progress;
        volatile PlaybackStatus playbackStatus;
        Window(String token) { this.token = token; }
    }
}
