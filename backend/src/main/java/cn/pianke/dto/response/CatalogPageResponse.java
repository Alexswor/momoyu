package cn.pianke.dto.response;

import java.util.List;

public record CatalogPageResponse(List<DramaResponse> items, int page, int pageCount, long total) {}
