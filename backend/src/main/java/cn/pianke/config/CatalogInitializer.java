package cn.pianke.config;

import cn.pianke.entity.Drama;
import cn.pianke.entity.Episode;
import cn.pianke.mapper.DramaMapper;
import cn.pianke.mapper.EpisodeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
public class CatalogInitializer implements CommandLineRunner {
    private final DramaMapper dramaMapper;
    private final EpisodeMapper episodeMapper;
    private final ObjectMapper objectMapper;

    public CatalogInitializer(DramaMapper dramaMapper, EpisodeMapper episodeMapper, ObjectMapper objectMapper) {
        this.dramaMapper = dramaMapper;
        this.episodeMapper = episodeMapper;
        this.objectMapper = objectMapper;
    }

    public record Catalog(List<Drama> dramas, List<Episode> episodes) {}

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Keep existing operator-managed catalogs. The bundled catalog seeds an empty database only.
        if (dramaMapper.selectCount(null) > 0) return;
        try (var input = new ClassPathResource("catalog.json").getInputStream()) {
            Catalog catalog = objectMapper.readValue(input, Catalog.class);
            for (Drama drama : catalog.dramas()) dramaMapper.insert(drama);
            for (Episode episode : catalog.episodes()) episodeMapper.insert(episode);
        }
    }
}
