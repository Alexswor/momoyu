package cn.pianke.service;

import cn.pianke.dto.response.DramaResponse;
import cn.pianke.dto.response.EpisodeResponse;
import cn.pianke.dto.response.PlaybackResponse;
import cn.pianke.entity.Drama;
import cn.pianke.entity.Episode;
import cn.pianke.exception.BizException;
import cn.pianke.mapper.DramaMapper;
import cn.pianke.mapper.EpisodeMapper;
import cn.pianke.service.convert.DramaConverter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class DramaService {
    private final DramaMapper dramaMapper;
    private final EpisodeMapper episodeMapper;
    private final MaccmsCatalogService remote;
    private final boolean isRemoteEnabled;

    public DramaService(DramaMapper dramaMapper, EpisodeMapper episodeMapper, MaccmsCatalogService remote,
            @Value("${app.catalog.remote-enabled:true}") boolean isRemoteEnabled) {
        this.dramaMapper = dramaMapper;
        this.episodeMapper = episodeMapper;
        this.remote = remote;
        this.isRemoteEnabled = isRemoteEnabled;
    }

    public List<DramaResponse> findDramas(String keyword, String category, String sort) {
        return findDramas(keyword, category, sort, "zy360", 1);
    }

    public List<DramaResponse> findDramas(String keyword, String category, String sort, String source, int page) {
        if (isRemoteEnabled && !"open".equals(source)) return remote.findDramas(keyword, category, sort, source, page);
        var query = new LambdaQueryWrapper<Drama>();
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            query.and(q -> q.like(Drama::getTitle, term).or().like(Drama::getTags, term));
        }
        query.eq(category != null && !category.isBlank(), Drama::getCategory, category);
        switch (sort) {
            case "newest" -> query.orderByDesc(Drama::getReleaseYear).orderByAsc(Drama::getSortOrder);
            case "duration" -> query.orderByAsc(Drama::getDuration);
            case "featured" -> query.orderByAsc(Drama::getSortOrder);
            default -> throw new BizException(HttpStatus.BAD_REQUEST, "排序方式不正确");
        }
        return dramaMapper.selectList(query).stream().map(DramaConverter::toResponse).toList();
    }

    public DramaResponse getDrama(String id) {
        if (remote.isRemoteId(id)) return remote.getDrama(id);
        Drama drama = dramaMapper.selectById(id);
        if (drama == null) throw new BizException(HttpStatus.NOT_FOUND, "剧集不存在");
        return DramaConverter.toResponse(drama);
    }

    public List<EpisodeResponse> getEpisodes(String id) {
        if (remote.isRemoteId(id)) return remote.getEpisodes(id);
        getDrama(id);
        return episodeMapper.selectList(new LambdaQueryWrapper<Episode>().eq(Episode::getDramaId, id)
                .orderByAsc(Episode::getEpisodeNumber)).stream()
                .map(e -> new EpisodeResponse(e.getEpisodeNumber(), e.getTitle(), e.getDuration())).toList();
    }

    public PlaybackResponse getPlayback(String id, int number) {
        if (remote.isRemoteId(id)) return remote.getPlayback(id, number);
        getDrama(id);
        Episode episode = episodeMapper.selectOne(new LambdaQueryWrapper<Episode>()
                .eq(Episode::getDramaId, id).eq(Episode::getEpisodeNumber, number));
        if (episode == null) throw new BizException(HttpStatus.NOT_FOUND, "该集不存在");
        // Start at the lower bitrate so slower international links can start promptly.
        List<String> urls = Stream.of(episode.getFallbackUrl(), episode.getVideoUrl())
                .filter(Objects::nonNull).filter(url -> !url.isBlank()).distinct().toList();
        return new PlaybackResponse(id, number, episode.getTitle(), urls, List.of("流畅", "高清"));
    }
}
