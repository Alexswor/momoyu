package cn.pianke.service;

import cn.pianke.dto.response.DramaResponse;
import cn.pianke.dto.response.EpisodeResponse;
import cn.pianke.dto.response.PlaybackResponse;
import cn.pianke.dto.response.PosterResponse;
import cn.pianke.dto.response.CatalogPageResponse;
import java.io.InputStream;
import cn.pianke.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Read-only adapter for the public MacCMS sources selected by the user. */
@Service
public class MaccmsCatalogService {
    private static final Map<String, Provider> PROVIDERS = Map.of(
            "bf", new Provider("暴风资源", "https://bfzyapi.com/api.php/provide/vod/", 68, "",
                    List.of("bfvp26.com")),
            "zy360", new Provider("360资源", "https://360zy.com/api.php/provide/vod/", 0, "AI",
                    List.of("imgzy360.com", "youkupic.com")));
    private static final Pattern AI_MARKER = Pattern.compile("(?i)(?:^|\\s|【|\\[)AI(?:\\s|漫剧|版|$)");
    private static final Pattern EPISODE_COUNT = Pattern.compile("全?(\\d+)集");
    private final HttpClient client;
    private final ObjectMapper json;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Cached> details = new ConcurrentHashMap<>();
    private final Map<String, PosterCache> posters = new ConcurrentHashMap<>();

    @Autowired
    public MaccmsCatalogService(ObjectMapper json) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    MaccmsCatalogService(ObjectMapper json, HttpClient client) { this.json = json; this.client = client; }

    private record Provider(String name, String api, int categoryId, String defaultKeyword, List<String> imageHosts) {}
    private record Cached(JsonNode value, long expiresAt) {}
    private record RemoteEpisode(String title, String url) {}
    private record PosterCache(PosterResponse response, long expiresAt) {}

    public boolean isRemoteId(String id) { return id != null && id.matches("(bf|zy360)-[0-9]+"); }

    public List<DramaResponse> findDramas(String keyword, String category, String sort, String source, int page) {
        return getCatalogPage(keyword, category, sort, source, page, 20).items();
    }

    public CatalogPageResponse getCatalogPage(String keyword, String category, String sort, String source, int page, int size) {
        Provider provider = requireProvider(source);
        if (!List.of("featured", "newest", "duration").contains(sort)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "排序方式不正确");
        }
        String search = keyword == null || keyword.isBlank() ? provider.defaultKeyword() : keyword.trim();
        String query = "?ac=detail&pg=" + page + "&pagesize=" + size + "&limit=" + size
                + (provider.categoryId() > 0 ? "&t=" + provider.categoryId() : "")
                + (search.isBlank() ? "" : "&wd=" + URLEncoder.encode(search, StandardCharsets.UTF_8));
        JsonNode data = fetch(provider.api() + query, provider.name());
        List<DramaResponse> result = new ArrayList<>();
        for (JsonNode item : data.path("list")) {
            String name = text(item, "vod_name");
            boolean isManga = (AI_MARKER.matcher(name).find() && text(item, "type_name").contains("短剧")) || name.contains("漫剧")
                    || text(item, "type_name").contains("漫剧") || text(item, "vod_class").contains("漫剧");
            if (!isManga || parseEpisodes(item).isEmpty()) continue;
            String id = source + "-" + item.path("vod_id").asText();
            details.put(id, new Cached(item, System.currentTimeMillis() + 120_000));
            DramaResponse drama = convert(id, item, provider);
            if (category == null || category.isBlank() || category.equals(drama.category())
                    || drama.tags().contains(category)) result.add(drama);
        }
        if ("newest".equals(sort)) result.sort(Comparator.comparing(DramaResponse::updatedAt).reversed());
        // Public sources do not report reliable popularity or episode duration. Do not invent either.
        if (details.size() > 2500) details.clear();
        return new CatalogPageResponse(result, page, data.path("pagecount").asInt(0), data.path("total").asLong(0));
    }

    public DramaResponse getDrama(String id) {
        return convert(id, getRawDetail(id), requireProvider(id.split("-", 2)[0]));
    }

    public List<EpisodeResponse> getEpisodes(String id) {
        List<RemoteEpisode> items = parseEpisodes(getRawDetail(id));
        List<EpisodeResponse> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) result.add(new EpisodeResponse(i + 1, items.get(i).title(), 0));
        return result;
    }

    public PlaybackResponse getPlayback(String id, int number) {
        List<RemoteEpisode> episodes = parseEpisodes(getRawDetail(id));
        if (number < 1 || number > episodes.size()) throw new BizException(HttpStatus.NOT_FOUND, "该集不存在");
        RemoteEpisode episode = episodes.get(number - 1);
        return new PlaybackResponse(id, number, episode.title(), List.of(episode.url()), List.of("原画"));
    }

    public PosterResponse getPoster(String id) {
        PosterCache cached = posters.get(id);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.response();
        String picture = text(getRawDetail(id), "vod_pic");
        Provider provider = requireProvider(id.split("-", 2)[0]);
        try {
            URI uri = URI.create(picture);
            String host = uri.getHost();
            // Only provider-owned image hosts are accepted. The caller can pass an ID, never a URL.
            boolean isAllowed = host != null && provider.imageHosts().stream()
                    .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
            if (!isAllowed || !List.of("https", "http").contains(uri.getScheme())) {
                throw new BizException(HttpStatus.BAD_GATEWAY, "封面地址不可用");
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0").header("Referer", provider.api())
                    .GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                String contentType = response.headers().firstValue("content-type").orElse("").split(";")[0];
                if (response.statusCode() != 200 || !contentType.startsWith("image/")) {
                    throw new BizException(HttpStatus.BAD_GATEWAY, "封面暂时无法加载");
                }
                byte[] bytes = input.readNBytes(2_000_001);
                if (bytes.length > 2_000_000) throw new BizException(HttpStatus.BAD_GATEWAY, "封面文件过大");
                PosterResponse result = new PosterResponse(bytes, contentType);
                if (posters.size() >= 48) posters.clear();
                posters.put(id, new PosterCache(result, System.currentTimeMillis() + 21_600_000));
                return result;
            }
        } catch (BizException error) { throw error; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "封面加载中断");
        } catch (Exception error) { throw new BizException(HttpStatus.BAD_GATEWAY, "封面暂时无法加载"); }
    }

    private JsonNode getRawDetail(String id) {
        if (!isRemoteId(id)) throw new BizException(HttpStatus.NOT_FOUND, "剧集不存在");
        Cached cached = details.get(id);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.value();
        String[] parts = id.split("-", 2);
        Provider provider = requireProvider(parts[0]);
        JsonNode data = fetch(provider.api() + "?ac=detail&ids=" + parts[1], provider.name());
        if (data.path("list").isEmpty()) throw new BizException(HttpStatus.NOT_FOUND, "剧集不存在或已下架");
        JsonNode item = data.path("list").get(0);
        details.put(id, new Cached(item, System.currentTimeMillis() + 120_000));
        return item;
    }

    private JsonNode fetch(String url, String sourceName) {
        Cached cached = cache.get(url);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.value();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(18))
                    .header("User-Agent", "Mozilla/5.0").header("Accept", "application/json").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BizException(HttpStatus.BAD_GATEWAY, sourceName + "暂不可用，请切换片源");
            }
            JsonNode value = json.readTree(response.body());
            if (value.path("code").asInt() != 1 || !value.path("list").isArray()) {
                throw new BizException(HttpStatus.BAD_GATEWAY, sourceName + "返回数据异常");
            }
            if (cache.size() > 200) cache.clear();
            cache.put(url, new Cached(value, System.currentTimeMillis() + 120_000));
            return value;
        } catch (BizException error) { throw error; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "片源请求已中断，请重试");
        } catch (Exception error) {
            throw new BizException(HttpStatus.BAD_GATEWAY, sourceName + "连接失败，请稍后重试或切换片源");
        }
    }

    private DramaResponse convert(String id, JsonNode item, Provider provider) {
        String rawTitle = text(item, "vod_name");
        String title = rawTitle.replaceAll("(?i)\\s*(AI\\s*(?:漫剧|短剧)(?:版)?|AI|漫剧)\\s*$", "").trim();
        String category = classify(title);
        String status = text(item, "vod_remarks");
        List<RemoteEpisode> episodes = parseEpisodes(item);
        boolean compilation = episodes.size() == 1 && (status.contains("全集") || episodes.get(0).title().contains("全集"));
        var countMatcher = EPISODE_COUNT.matcher(status);
        int originalCount = countMatcher.find() ? Integer.parseInt(countMatcher.group(1))
                : compilation ? item.path("vod_total").asInt(0) : episodes.size();
        List<String> tags = new ArrayList<>();
        tags.add("漫剧");
        if (!"漫剧".equals(category)) tags.add(category);
        if (!text(item, "vod_lang").isBlank()) tags.add(text(item, "vod_lang"));
        String yearText = text(item, "vod_year").replaceAll("[^0-9]", "");
        int year = yearText.length() == 4 ? Integer.parseInt(yearText) : 0;
        String synopsis = text(item, "vod_content").replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&").trim();
        String picture = "/api/dramas/" + id + "/poster";
        return new DramaResponse(id, title, category, tags, synopsis, picture, picture, year, 0, episodes.size(),
                provider.name(), "第三方公开片源", provider.api() + "?ac=detail&ids=" + item.path("vod_id").asText(),
                status.isBlank() ? "漫剧" : status, compilation, originalCount, provider.name(), text(item, "vod_time"));
    }

    private static String classify(String title) {
        if (title.matches(".*(末世|丧尸|废土|星际|机甲|末日).*")) return "末世";
        if (title.matches(".*(王爷|千岁|宫宴|娘娘|将军|侯府|皇后|古代).*")) return "古风";
        if (title.matches(".*(大帝|修仙|天尊|宗门|仙界|天道|魔尊|灵气|玄幻).*")) return "玄幻";
        if (title.matches(".*(都市|高考|总裁|首富|职场|老板|婚礼|全村|大巴).*")) return "都市";
        if (title.matches(".*(重生|逆袭|复仇|反派|系统|攻略).*")) return "逆袭";
        return "漫剧";
    }

    private static List<RemoteEpisode> parseEpisodes(JsonNode item) {
        String[] groups = text(item, "vod_play_url").split("\\$\\$\\$");
        List<RemoteEpisode> best = List.of();
        for (String group : groups) {
            Map<String, RemoteEpisode> parsed = new LinkedHashMap<>();
            for (String entry : group.split("#")) {
                int separator = entry.indexOf('$');
                if (separator < 0) continue;
                String title = entry.substring(0, separator).trim();
                String url = entry.substring(separator + 1).trim();
                // Player pages are not video sources. Only publish actual media playlists/files.
                if (url.matches("https?://[^\\s]+\\.(m3u8|mp4)(\\?[^\\s]*)?")) {
                    parsed.putIfAbsent(url, new RemoteEpisode(title, url));
                }
            }
            if (parsed.size() > best.size()) best = new ArrayList<>(parsed.values());
        }
        return best;
    }

    private static String text(JsonNode item, String field) { return item.path(field).asText(""); }

    private static Provider requireProvider(String key) {
        Provider provider = PROVIDERS.get(key);
        if (provider == null) throw new BizException(HttpStatus.BAD_REQUEST, "片源不存在");
        return provider;
    }
}
