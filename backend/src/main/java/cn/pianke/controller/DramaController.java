package cn.pianke.controller;

import cn.pianke.common.Result;
import cn.pianke.dto.response.DramaResponse;
import cn.pianke.dto.response.EpisodeResponse;
import cn.pianke.dto.response.PlaybackResponse;
import cn.pianke.dto.response.CatalogPageResponse;
import cn.pianke.service.DramaService;
import cn.pianke.service.MaccmsCatalogService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.time.Duration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DramaController {
    private final DramaService dramaService;
    private final MaccmsCatalogService remote;

    public DramaController(DramaService dramaService, MaccmsCatalogService remote) {
        this.dramaService = dramaService;
        this.remote = remote;
    }

    @GetMapping("/dramas/{id}/poster")
    public ResponseEntity<byte[]> poster(@PathVariable String id) {
        var poster = remote.getPoster(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(poster.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(poster.bytes());
    }

    @GetMapping("/health")
    public Result<Map<String, String>> health() { return Result.success(Map.of("status", "ok")); }

    @GetMapping("/catalog")
    public Result<CatalogPageResponse> catalog(@RequestParam(required = false) @Size(max = 80) String keyword,
            @RequestParam(defaultValue = "zy360") String source,
            @RequestParam(defaultValue = "1") @Min(1) @Max(500) int page,
            @RequestParam(defaultValue = "500") @Min(20) @Max(500) int size) {
        return Result.success(remote.getCatalogPage(keyword, null, "featured", source, page, size));
    }

    @GetMapping("/dramas")
    public Result<List<DramaResponse>> list(@RequestParam(required = false) @Size(max = 80) String keyword,
            @RequestParam(required = false) @Size(max = 40) String category,
            @RequestParam(defaultValue = "featured") String sort,
            @RequestParam(defaultValue = "zy360") String source,
            @RequestParam(defaultValue = "1") @Min(1) @Max(500) int page) {
        return Result.success(dramaService.findDramas(keyword, category, sort, source, page));
    }

    @GetMapping("/dramas/{id}")
    public Result<DramaResponse> detail(@PathVariable String id) {
        return Result.success(dramaService.getDrama(id));
    }

    @GetMapping("/dramas/{id}/episodes")
    public Result<List<EpisodeResponse>> episodes(@PathVariable String id) {
        return Result.success(dramaService.getEpisodes(id));
    }

    @GetMapping("/dramas/{id}/episodes/{number}/play")
    public Result<PlaybackResponse> playback(@PathVariable String id,
            @PathVariable @Min(1) @Max(10000) int number) {
        return Result.success(dramaService.getPlayback(id, number));
    }
}
