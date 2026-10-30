package cn.pianke.service.convert;

import cn.pianke.dto.response.DramaResponse;
import cn.pianke.entity.Drama;
import java.util.Arrays;

public final class DramaConverter {
    private DramaConverter() {}

    public static DramaResponse toResponse(Drama drama) {
        return new DramaResponse(drama.getId(), drama.getTitle(), drama.getCategory(),
                Arrays.asList(drama.getTags().split(",")), drama.getSynopsis(), drama.getPoster(),
                drama.getBackdrop(), drama.getReleaseYear(), drama.getDuration(), drama.getEpisodeCount(),
                drama.getCredit(), drama.getLicenseName(), drama.getSourcePage(), "完整短片", false,
                drama.getEpisodeCount(), "开放短片", "");
    }
}
