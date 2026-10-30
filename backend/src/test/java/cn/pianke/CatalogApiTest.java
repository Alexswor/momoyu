package cn.pianke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.catalog.remote-enabled=false",
        "app.cors-origins=http://127.0.0.1:5173",
        "spring.datasource.url=jdbc:h2:mem:catalogtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CatalogApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void everyPublishedEpisodeIsAvailableWithoutLogin() throws Exception {
        String body = mvc.perform(get("/api/dramas")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode catalog = json.readTree(body).path("data");
        assertThat(catalog.size()).isGreaterThanOrEqualTo(8);
        for (JsonNode drama : catalog) {
            String id = drama.path("id").asText();
            String episodeBody = mvc.perform(get("/api/dramas/{id}/episodes", id))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode episodes = json.readTree(episodeBody).path("data");
            assertThat(episodes.size()).isEqualTo(drama.path("episodeCount").asInt());
            for (int number = 1; number <= episodes.size(); number++) {
                mvc.perform(get("/api/dramas/{id}/episodes/{number}/play", id, number))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.urls[0]").isNotEmpty());
            }
        }
    }

    @Test
    void searchAndCategoryFilterUseStoredCatalog() throws Exception {
        mvc.perform(get("/api/dramas").param("keyword", "春日"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("spring"));
        mvc.perform(get("/api/dramas").param("category", "科幻"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].category").value("科幻"));
        mvc.perform(get("/api/dramas").param("keyword", "不存在的作品"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void invalidRequestsHaveMeaningfulHttpStatus() throws Exception {
        mvc.perform(get("/api/dramas/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/dramas/spring/episodes/99/play")).andExpect(status().isNotFound());
        mvc.perform(get("/api/dramas/spring/episodes/0/play")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dramas").param("sort", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dramas").param("keyword", "x".repeat(81))).andExpect(status().isBadRequest());
    }

    @Test
    void approvedOriginsCanReadPublicApi() throws Exception {
        mvc.perform(get("/api/dramas").header("Origin", "http://127.0.0.1:5173"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dramas").header("Origin", "https://unapproved.example"))
                .andExpect(status().isForbidden());
    }
}
