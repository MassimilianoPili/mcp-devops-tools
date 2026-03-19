package io.github.massimilianopili.mcp.devops;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@ConditionalOnProperty(name = "mcp.devops.pat")
public class DevOpsBoardTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsBoardTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_list_sprints",
          description = "Lists all iterations/sprints of the Azure DevOps team with start and finish dates")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listSprints() {
        return webClient.get()
                .uri(props.getTeamBaseUrl()
                        + "/_apis/work/teamsettings/iterations?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) {
                        return List.<Map<String, Object>>of();
                    }
                    List<Map<String, Object>> iterations = (List<Map<String, Object>>) response.get("value");
                    return iterations.stream().map(it -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", it.getOrDefault("id", ""));
                        result.put("name", it.getOrDefault("name", ""));
                        result.put("path", it.getOrDefault("path", ""));
                        Object attrs = it.get("attributes");
                        if (attrs instanceof Map) {
                            Map<String, Object> a = (Map<String, Object>) attrs;
                            result.put("startDate", a.getOrDefault("startDate", ""));
                            result.put("finishDate", a.getOrDefault("finishDate", ""));
                            result.put("timeFrame", a.getOrDefault("timeFrame", ""));
                        }
                        return result;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero sprint: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_get_sprint_work_items",
          description = "Retrieves work items associated with a specific sprint/iteration (returns IDs and hierarchical relations)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getSprintWorkItems(
            @ToolParam(description = "Iteration/sprint ID (UUID)") String iterationId) {
        return webClient.get()
                .uri(props.getTeamBaseUrl()
                        + "/_apis/work/teamsettings/iterations/" + iterationId
                        + "/workitems?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero work items sprint: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_get_board_columns",
          description = "Retrieves columns of an Azure DevOps board with WIP limits and state mappings")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> getBoardColumns(
            @ToolParam(description = "Board name, e.g. Stories, Bugs, Backlog items") String boardName) {
        return webClient.get()
                .uri(props.getTeamBaseUrl()
                        + "/_apis/work/boards/" + boardName
                        + "/columns?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) {
                        return List.<Map<String, Object>>of();
                    }
                    List<Map<String, Object>> columns = (List<Map<String, Object>>) response.get("value");
                    return columns.stream().map(col -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", col.getOrDefault("id", ""));
                        result.put("name", col.getOrDefault("name", ""));
                        result.put("columnType", col.getOrDefault("columnType", ""));
                        result.put("itemLimit", col.getOrDefault("itemLimit", 0));
                        result.put("isSplit", col.getOrDefault("isSplit", false));
                        result.put("stateMappings", col.getOrDefault("stateMappings", Map.of()));
                        return result;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero colonne board: " + e.getMessage()))));
    }
}
