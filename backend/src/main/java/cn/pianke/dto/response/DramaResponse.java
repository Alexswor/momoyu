package cn.pianke.dto.response;

import java.util.List;

public record DramaResponse(String id, String title, String category, List<String> tags,
        String synopsis, String poster, String backdrop, int year, int duration, int episodeCount,
        String credit, String licenseName, String sourcePage, String status, boolean compilation,
        int originalEpisodeCount, String sourceName, String updatedAt) {}
