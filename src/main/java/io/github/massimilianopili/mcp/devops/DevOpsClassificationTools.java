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
public class DevOpsClassificationTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsClassificationTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_list_area_paths",
          description = "Elenca le area path (classificazione aree) del progetto Azure DevOps con struttura gerarchica")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> listAreaPaths() {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/wit/classificationnodes/areas?$depth=10&api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore lista area paths: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_create_area_path",
          description = "Crea una nuova area path nel progetto Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createAreaPath(
            @ToolParam(description = "Path padre dove creare il nodo, es: '' per root o 'AreaPadre' per sotto-area") String parentPath,
            @ToolParam(description = "Nome del nuovo nodo area") String name) {
        String endpoint = props.getBaseUrl() + "/_apis/wit/classificationnodes/areas";
        if (parentPath != null && !parentPath.isBlank()) {
            endpoint += "/" + parentPath;
        }
        endpoint += "?api-version=" + props.getApiVersion();

        return webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", name))
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione area path: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_delete_area_path",
          description = "Elimina una area path dal progetto Azure DevOps. I work item associati vengono reclassificati alla root.")
    public Mono<Map<String, Object>> deleteAreaPath(
            @ToolParam(description = "Path del nodo area da eliminare, es: 'Area1' o 'Area1/SubArea'") String path) {
        return webClient.delete()
                .uri(props.getBaseUrl() + "/_apis/wit/classificationnodes/areas/" + path
                        + "?$reclassifyId=1&api-version=" + props.getApiVersion())
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "deleted", true))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore eliminazione area path: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_list_iteration_paths",
          description = "Elenca le iteration path (sprint/iterazioni) del progetto Azure DevOps con struttura gerarchica")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> listIterationPaths() {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/wit/classificationnodes/iterations?$depth=10&api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore lista iteration paths: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_create_iteration_path",
          description = "Crea una nuova iteration path (sprint) nel progetto Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createIterationPath(
            @ToolParam(description = "Path padre dove creare il nodo, es: '' per root o 'Release1'") String parentPath,
            @ToolParam(description = "Nome della nuova iterazione") String name,
            @ToolParam(description = "Data di inizio in formato ISO 8601, es: 2026-01-01T00:00:00Z", required = false) String startDate,
            @ToolParam(description = "Data di fine in formato ISO 8601, es: 2026-01-14T00:00:00Z", required = false) String finishDate) {

        String endpoint = props.getBaseUrl() + "/_apis/wit/classificationnodes/iterations";
        if (parentPath != null && !parentPath.isBlank()) {
            endpoint += "/" + parentPath;
        }
        endpoint += "?api-version=" + props.getApiVersion();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        if (startDate != null && !startDate.isBlank()) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("startDate", startDate);
            if (finishDate != null && !finishDate.isBlank()) {
                attributes.put("finishDate", finishDate);
            }
            body.put("attributes", attributes);
        }

        return webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione iteration path: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_update_iteration_path",
          description = "Aggiorna le date di un'iteration path (sprint) esistente nel progetto Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> updateIterationPath(
            @ToolParam(description = "Path dell'iterazione da aggiornare, es: 'Sprint 1'") String path,
            @ToolParam(description = "Nuova data di inizio in formato ISO 8601, es: 2026-01-01T00:00:00Z", required = false) String startDate,
            @ToolParam(description = "Nuova data di fine in formato ISO 8601, es: 2026-01-14T00:00:00Z", required = false) String finishDate) {

        Map<String, Object> attributes = new LinkedHashMap<>();
        if (startDate != null && !startDate.isBlank()) attributes.put("startDate", startDate);
        if (finishDate != null && !finishDate.isBlank()) attributes.put("finishDate", finishDate);

        return webClient.patch()
                .uri(props.getBaseUrl() + "/_apis/wit/classificationnodes/iterations/" + path
                        + "?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("attributes", attributes))
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore aggiornamento iteration path: " + e.getMessage())));
    }
}
