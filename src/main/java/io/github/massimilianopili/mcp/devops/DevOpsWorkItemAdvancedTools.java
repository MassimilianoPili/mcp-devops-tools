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
public class DevOpsWorkItemAdvancedTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsWorkItemAdvancedTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_delete_work_item",
          description = "Elimina un work item in Azure DevOps (soft delete, recuperabile dal recycle bin)")
    public Mono<Map<String, Object>> deleteWorkItem(
            @ToolParam(description = "ID del work item da eliminare") int workItemId) {
        return webClient.delete()
                .uri(props.getBaseUrl() + "/_apis/wit/workitems/" + workItemId
                        + "?destroy=false&api-version=" + props.getApiVersion())
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "deleted", true, "workItemId", workItemId))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore eliminazione work item " + workItemId + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_add_work_item_link",
          description = "Aggiunge un link/relazione tra due work item in Azure DevOps. Tipi comuni: System.LinkTypes.Hierarchy-Forward (parent-child), System.LinkTypes.Related")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> addWorkItemLink(
            @ToolParam(description = "ID del work item sorgente") int workItemId,
            @ToolParam(description = "URL completo del work item target, es: https://dev.azure.com/{org}/{project}/_apis/wit/workitems/{id}") String targetUrl,
            @ToolParam(description = "Tipo di relazione, es: System.LinkTypes.Hierarchy-Forward, System.LinkTypes.Related") String relationType,
            @ToolParam(description = "Commento opzionale per il link", required = false) String comment) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("rel", relationType);
        value.put("url", targetUrl);
        if (comment != null && !comment.isBlank()) {
            value.put("attributes", Map.of("comment", comment));
        }

        List<Map<String, Object>> patchOps = List.of(
                Map.of("op", "add", "path", "/relations/-", "value", value)
        );

        return webClient.patch()
                .uri(props.getBaseUrl() + "/_apis/wit/workitems/" + workItemId + "?api-version=" + props.getApiVersion())
                .contentType(MediaType.valueOf("application/json-patch+json"))
                .bodyValue(patchOps)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore aggiunta link work item: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_remove_work_item_link",
          description = "Rimuove un link/relazione da un work item in Azure DevOps tramite indice della relazione")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> removeWorkItemLink(
            @ToolParam(description = "ID del work item") int workItemId,
            @ToolParam(description = "Indice (0-based) della relazione nell'array relations del work item") int relationIndex) {
        List<Map<String, Object>> patchOps = List.of(
                Map.of("op", "remove", "path", "/relations/" + relationIndex)
        );

        return webClient.patch()
                .uri(props.getBaseUrl() + "/_apis/wit/workitems/" + workItemId + "?api-version=" + props.getApiVersion())
                .contentType(MediaType.valueOf("application/json-patch+json"))
                .bodyValue(patchOps)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore rimozione link work item: " + e.getMessage())));
    }
}
