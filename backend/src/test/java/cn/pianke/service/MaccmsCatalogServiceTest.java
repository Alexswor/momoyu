package cn.pianke.service;

import cn.pianke.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MaccmsCatalogServiceTest {
    private final HttpClient client = mock(HttpClient.class);
    private final MaccmsCatalogService catalog = new MaccmsCatalogService(new ObjectMapper(), client);

    private static final String RESPONSE = """
            {"code":1,"page":1,"pagecount":804,"total":16063,"list":[
              {"vod_id":101,"vod_name":"海风奇遇AI短剧","type_name":"AI漫剧","vod_remarks":"完结",
               "vod_total":60,"vod_pic":"https://p.bfvp26.com/test.png",
               "vod_play_url":"全集$https://media.example/complete.m3u8"},
              {"vod_id":102,"vod_name":"普通电影","type_name":"电影",
               "vod_play_url":"正片$https://media.example/movie.mp4"}
            ]}
            """;

    @SuppressWarnings("unchecked")
    private void respondWith(String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
    }

    @Test
    void baofengUsesAiCategoryWithSearchAndKeepsOneRealCompilation() throws Exception {
        respondWith(RESPONSE);
        var page = catalog.getCatalogPage("海风", null, "featured", "bf", 804, 100);
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any());
        String url = request.getValue().uri().toString();
        assertThat(url).startsWith("https://bfzyapi.com/api.php/provide/vod/")
                .contains("&t=68", "&wd=%E6%B5%B7%E9%A3%8E", "pg=804", "pagesize=100");
        assertThat(page.pageCount()).isEqualTo(804);
        assertThat(page.items()).hasSize(1);
        var drama = page.items().get(0);
        assertThat(drama.id()).isEqualTo("bf-101");
        assertThat(drama.title()).isEqualTo("海风奇遇");
        assertThat(drama.sourceName()).isEqualTo("暴风资源");
        assertThat(drama.compilation()).isTrue();
        assertThat(drama.originalEpisodeCount()).isEqualTo(60);
        assertThat(catalog.getEpisodes(drama.id())).hasSize(1);
        assertThat(catalog.getPlayback(drama.id(), 1).urls()).containsExactly("https://media.example/complete.m3u8");
        assertThatThrownBy(() -> catalog.getPlayback(drama.id(), 2)).isInstanceOf(BizException.class);
        catalog.getCatalogPage("海风", null, "featured", "bf", 804, 100);
        verifyNoMoreInteractions(client);
    }

    @Test
    void retiredSourcesAreRejectedBeforeAnyNetworkRequest() {
        for (String source : List.of("wj", "ff")) {
            assertThat(catalog.isRemoteId(source + "-101")).isFalse();
            assertThatThrownBy(() -> catalog.getCatalogPage(null, null, "featured", source, 1, 100))
                    .isInstanceOf(BizException.class).hasMessage("片源不存在");
            assertThatThrownBy(() -> catalog.getDrama(source + "-101")).isInstanceOf(BizException.class);
        }
        verifyNoInteractions(client);
    }

    @Test
    void imageHostSuffixCannotEscapeBaofengAllowlist() throws Exception {
        respondWith(RESPONSE.replace("p.bfvp26.com", "p.bfvp26.com.attacker.example"));
        catalog.getCatalogPage(null, null, "featured", "bf", 1, 100);
        assertThatThrownBy(() -> catalog.getPoster("bf-101")).isInstanceOf(BizException.class)
                .hasMessage("封面地址不可用");
        verify(client).send(any(), any());
        verifyNoMoreInteractions(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void baofengPosterBytesAreCachedWithoutRecompression() throws Exception {
        respondWith(RESPONSE);
        catalog.getCatalogPage(null, null, "featured", "bf", 1, 100);
        byte[] image = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jH2kAAAAASUVORK5CYII=");
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/png")), (a, b) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(image));
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(response);
        assertThat(catalog.getPoster("bf-101").bytes()).isEqualTo(image);
        assertThat(catalog.getPoster("bf-101").bytes()).isEqualTo(image);
        verify(client, times(2)).send(any(), any());
    }
}
