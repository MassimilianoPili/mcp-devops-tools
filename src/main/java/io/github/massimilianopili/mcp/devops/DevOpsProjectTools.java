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
public class DevOpsProjectTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsProjectTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_list_projects",
          description = "Elenca tutti i progetti nell'organizzazione Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listProjects() {
        return webClient.get()
                .uri(props.getOrgBaseUrl() + "/_apis/projects?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) return List.<Map<String, Object>>of();
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("value");
                    return items.stream().map(p -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", p.getOrDefault("id", ""));
                        r.put("name", p.getOrDefault("name", ""));
                        r.put("description", p.getOrDefault("description", ""));
                        r.put("state", p.getOrDefault("state", ""));
                        r.put("visibility", p.getOrDefault("visibility", ""));
                        return r;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista progetti: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_get_project",
          description = "Recupera i dettagli di un progetto Azure DevOps per ID o nome")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getProject(
            @ToolParam(description = "ID o nome del progetto") String projectId) {
        return webClient.get()
                .uri(props.getOrgBaseUrl() + "/_apis/projects/" + projectId + "?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero progetto: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_create_project",
          description = "Crea un nuovo progetto nell'organizzazione Azure DevOps. Restituisce un operationId per monitorare lo stato (usa devops_get_operation_status)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createProject(
            @ToolParam(description = "Nome del progetto") String name,
            @ToolParam(description = "Descrizione del progetto", required = false) String description,
            @ToolParam(description = "Visibilità: private o public (default: private)", required = false) String visibility,
            @ToolParam(description = "ID del processo template (es: Agile, Scrum, CMMI). Ometti per Agile.", required = false) String processTemplateId) {

        String vis = (visibility != null && !visibility.isBlank()) ? visibility : "private";
        String templateId = (processTemplateId != null && !processTemplateId.isBlank())
                ? processTemplateId : "adcc42ab-9882-485e-a3ed-7678f01f66bc"; // Agile

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", description != null ? description : "");
        body.put("visibility", vis);
        body.put("capabilities", Map.of(
                "versioncontrol", Map.of("sourceControlType", "Git"),
                "processTemplate", Map.of("templateTypeId", templateId)
        ));

        return webClient.post()
                .uri(props.getOrgBaseUrl() + "/_apis/projects?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione progetto: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_get_operation_status",
          description = "Verifica lo stato di un'operazione asincrona Azure DevOps (es: creazione progetto). Stato: notSet, queued, inProgress, cancelled, succeeded, failed")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getOperationStatus(
            @ToolParam(description = "ID dell'operazione (restituito da devops_create_project)") String operationId) {
        return webClient.get()
                .uri(props.getOrgBaseUrl() + "/_apis/operations/" + operationId + "?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero stato operazione: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_list_project_teams",
          description = "Elenca i team di un progetto Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listProjectTeams(
            @ToolParam(description = "ID o nome del progetto") String projectId) {
        return webClient.get()
                .uri(props.getOrgBaseUrl() + "/_apis/projects/" + projectId + "/teams?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) return List.<Map<String, Object>>of();
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("value");
                    return items.stream().map(t -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", t.getOrDefault("id", ""));
                        r.put("name", t.getOrDefault("name", ""));
                        r.put("description", t.getOrDefault("description", ""));
                        return r;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista team: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_create_team",
          description = "Crea un nuovo team in un progetto Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createTeam(
            @ToolParam(description = "ID o nome del progetto") String projectId,
            @ToolParam(description = "Nome del team") String name,
            @ToolParam(description = "Descrizione del team", required = false) String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        if (description != null && !description.isBlank()) body.put("description", description);

        return webClient.post()
                .uri(props.getOrgBaseUrl() + "/_apis/projects/" + projectId + "/teams?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione team: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_delete_team",
          description = "Elimina un team da un progetto Azure DevOps")
    public Mono<Map<String, Object>> deleteTeam(
            @ToolParam(description = "ID o nome del progetto") String projectId,
            @ToolParam(description = "ID del team da eliminare") String teamId) {
        return webClient.delete()
                .uri(props.getOrgBaseUrl() + "/_apis/projects/" + projectId + "/teams/" + teamId + "?api-version=" + props.getApiVersion())
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "deleted", true))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore eliminazione team: " + e.getMessage())));
    }
}
