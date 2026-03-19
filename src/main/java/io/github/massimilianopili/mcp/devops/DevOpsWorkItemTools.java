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
public class DevOpsWorkItemTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsWorkItemTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_query_work_items",
          description = "Executes a WIQL query on Azure DevOps and returns matching work items with key fields (ID, title, state, type, assignee). Maximum 200 results.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> queryWorkItems(
            @ToolParam(description = "WIQL query, e.g. SELECT [System.Id], [System.Title] FROM workitems WHERE [System.State] = 'Active'")
            String wiqlQuery) {
        return webClient.post()
                .uri(props.getBaseUrl() + "/_apis/wit/wiql?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", wiqlQuery))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(wiqlResult -> {
                    if (!wiqlResult.containsKey("workItems")) {
                        return Mono.just(Map.<String, Object>of("count", 0, "workItems", List.of()));
                    }

                    List<Map<String, Object>> wiqlItems = (List<Map<String, Object>>) wiqlResult.get("workItems");
                    if (wiqlItems.isEmpty()) {
                        return Mono.just(Map.<String, Object>of("count", 0, "workItems", List.of()));
                    }

                    List<Integer> ids = wiqlItems.stream()
                            .map(item -> (Integer) item.get("id"))
                            .limit(200)
                            .toList();

                    return webClient.post()
                            .uri(props.getBaseUrl() + "/_apis/wit/workitemsbatch?api-version=" + props.getApiVersion())
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of(
                                    "ids", ids,
                                    "fields", List.of(
                                            "System.Id", "System.Title", "System.State",
                                            "System.WorkItemType", "System.AssignedTo",
                                            "System.IterationPath", "System.AreaPath",
                                            "System.CreatedDate", "System.ChangedDate"
                                    )
                            ))
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(batchResult -> {
                                if (!batchResult.containsKey("value")) {
                                    return Map.<String, Object>of("count", 0, "workItems", List.of());
                                }
                                List<Map<String, Object>> items = (List<Map<String, Object>>) batchResult.get("value");
                                return Map.<String, Object>of("count", items.size(), "workItems", items);
                            });
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore query work items: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_get_work_item",
          description = "Retrieves a single Azure DevOps work item by ID, with all fields and optionally relations")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getWorkItem(
            @ToolParam(description = "Numeric work item ID") int workItemId,
            @ToolParam(description = "Expand: None, Relations, Fields, Links, All", required = false)
            String expand) {
        String uri = props.getBaseUrl() + "/_apis/wit/workitems/" + workItemId
                + "?api-version=" + props.getApiVersion();
        if (expand != null && !expand.isBlank()) {
            uri += "&$expand=" + expand;
        }
        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero work item " + workItemId + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_create_work_item",
          description = "Creates a new work item in Azure DevOps (Bug, Task, User Story, Feature, Epic)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createWorkItem(
            @ToolParam(description = "Type: Bug, Task, User Story, Feature, Epic") String workItemType,
            @ToolParam(description = "Work item title") String title,
            @ToolParam(description = "Description (HTML supported)", required = false) String description,
            @ToolParam(description = "Initial state, e.g. New, Active", required = false) String state,
            @ToolParam(description = "Assignee (email or display name)", required = false) String assignedTo,
            @ToolParam(description = "Iteration path, e.g. ProjectName\\Sprint 1", required = false) String iterationPath,
            @ToolParam(description = "Area path", required = false) String areaPath) {
        List<Map<String, String>> patchOps = buildPatchDocument(
                title, description, state, assignedTo, iterationPath, areaPath);

        return webClient.post()
                .uri(props.getBaseUrl() + "/_apis/wit/workitems/$" + workItemType
                        + "?api-version=" + props.getApiVersion())
                .contentType(MediaType.valueOf("application/json-patch+json"))
                .bodyValue(patchOps)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione work item: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_update_work_item",
          description = "Updates an existing Azure DevOps work item. Specify only the fields to modify.")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> updateWorkItem(
            @ToolParam(description = "Work item ID to update") int workItemId,
            @ToolParam(description = "New title", required = false) String title,
            @ToolParam(description = "New description", required = false) String description,
            @ToolParam(description = "New state, e.g. Active, Resolved, Closed", required = false) String state,
            @ToolParam(description = "New assignee", required = false) String assignedTo,
            @ToolParam(description = "New iteration path", required = false) String iterationPath) {
        return Mono.defer(() -> {
            List<Map<String, String>> patchOps = buildPatchDocument(
                    title, description, state, assignedTo, iterationPath, null);

            if (patchOps.isEmpty()) {
                return Mono.just(Map.<String, Object>of("error", "Nessun campo da aggiornare specificato"));
            }

            return webClient.patch()
                    .uri(props.getBaseUrl() + "/_apis/wit/workitems/" + workItemId
                            + "?api-version=" + props.getApiVersion())
                    .contentType(MediaType.valueOf("application/json-patch+json"))
                    .bodyValue(patchOps)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(r -> (Map<String, Object>) r);
        })
        .onErrorResume(e -> Mono.just(Map.of("error", "Errore aggiornamento work item " + workItemId + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_search_work_items",
          description = "Searches work items by common filters (state, type, assignee, sprint, tag). "
                      + "All filters are optional. Without filters, returns recent work items.")
    public Mono<Map<String, Object>> searchWorkItems(
            @ToolParam(description = "State: New, Active, Resolved, Closed", required = false) String state,
            @ToolParam(description = "Type: Bug, Task, User Story, Feature, Epic", required = false) String workItemType,
            @ToolParam(description = "Assignee (email or name). Use '@me' for current user", required = false) String assignedTo,
            @ToolParam(description = "Iteration path or sprint name, e.g. Sprint 5", required = false) String iteration,
            @ToolParam(description = "Tag to filter by", required = false) String tag) {

        StringBuilder wiql = new StringBuilder(
                "SELECT [System.Id], [System.Title], [System.State], [System.WorkItemType], [System.AssignedTo] "
              + "FROM workitems WHERE [System.TeamProject] = @project");

        if (state != null && !state.isBlank()) {
            wiql.append(" AND [System.State] = '").append(state.replace("'", "''")).append("'");
        }
        if (workItemType != null && !workItemType.isBlank()) {
            wiql.append(" AND [System.WorkItemType] = '").append(workItemType.replace("'", "''")).append("'");
        }
        if (assignedTo != null && !assignedTo.isBlank()) {
            if ("@me".equalsIgnoreCase(assignedTo.trim())) {
                wiql.append(" AND [System.AssignedTo] = @me");
            } else {
                wiql.append(" AND [System.AssignedTo] = '").append(assignedTo.replace("'", "''")).append("'");
            }
        }
        if (iteration != null && !iteration.isBlank()) {
            wiql.append(" AND [System.IterationPath] UNDER '").append(iteration.replace("'", "''")).append("'");
        }
        if (tag != null && !tag.isBlank()) {
            wiql.append(" AND [System.Tags] CONTAINS '").append(tag.replace("'", "''")).append("'");
        }

        wiql.append(" ORDER BY [System.ChangedDate] DESC");

        return queryWorkItems(wiql.toString());
    }

    private Map<String, String> patchOp(String field, String value) {
        return Map.of("op", "add", "path", "/fields/" + field, "value", value);
    }

    private List<Map<String, String>> buildPatchDocument(
            String title, String description, String state,
            String assignedTo, String iterationPath, String areaPath) {
        List<Map<String, String>> ops = new ArrayList<>();
        if (title != null && !title.isBlank()) ops.add(patchOp("System.Title", title));
        if (description != null && !description.isBlank()) ops.add(patchOp("System.Description", description));
        if (state != null && !state.isBlank()) ops.add(patchOp("System.State", state));
        if (assignedTo != null && !assignedTo.isBlank()) ops.add(patchOp("System.AssignedTo", assignedTo));
        if (iterationPath != null && !iterationPath.isBlank()) ops.add(patchOp("System.IterationPath", iterationPath));
        if (areaPath != null && !areaPath.isBlank()) ops.add(patchOp("System.AreaPath", areaPath));
        return ops;
    }
}
