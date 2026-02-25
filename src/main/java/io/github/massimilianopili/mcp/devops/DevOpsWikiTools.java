package io.github.massimilianopili.mcp.devops;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@ConditionalOnProperty(name = "mcp.devops.pat")
public class DevOpsWikiTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsWikiTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_list_wikis",
          description = "Elenca tutte le wiki disponibili nel progetto Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listWikis() {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/wiki/wikis?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) return List.<Map<String, Object>>of();
                    List<Map<String, Object>> wikis = (List<Map<String, Object>>) response.get("value");
                    return wikis.stream().map(w -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", w.getOrDefault("id", ""));
                        r.put("name", w.getOrDefault("name", ""));
                        r.put("type", w.getOrDefault("type", ""));
                        r.put("url", w.getOrDefault("url", ""));
                        return r;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista wiki: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_get_wiki_page",
          description = "Recupera il contenuto di una pagina wiki in Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getWikiPage(
            @ToolParam(description = "ID o nome della wiki") String wikiId,
            @ToolParam(description = "Path della pagina, es: /Home o /Guide/Setup") String path) {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/wiki/wikis/" + wikiId
                        + "/pages?path=" + path + "&includeContent=true&api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero pagina wiki: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_create_wiki_page",
          description = "Crea o aggiorna una pagina wiki in Azure DevOps (formato Markdown)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createWikiPage(
            @ToolParam(description = "ID o nome della wiki") String wikiId,
            @ToolParam(description = "Path della pagina, es: /Home o /Guide/Nuova-Pagina") String path,
            @ToolParam(description = "Contenuto della pagina in formato Markdown") String content) {
        return webClient.put()
                .uri(props.getBaseUrl() + "/_apis/wiki/wikis/" + wikiId
                        + "/pages?path=" + path + "&api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "-1")
                .bodyValue(Map.of("content", content))
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione/aggiornamento pagina wiki: " + e.getMessage())));
    }
}
