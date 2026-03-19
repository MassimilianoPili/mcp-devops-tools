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
public class DevOpsPipelineTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsPipelineTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_list_pipelines",
          description = "Lists all pipelines in the Azure DevOps project")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listPipelines() {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/pipelines?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) {
                        return List.<Map<String, Object>>of();
                    }
                    List<Map<String, Object>> pipelines = (List<Map<String, Object>>) response.get("value");
                    return pipelines.stream().map(p -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", p.getOrDefault("id", 0));
                        result.put("name", p.getOrDefault("name", ""));
                        result.put("folder", p.getOrDefault("folder", ""));
                        result.put("revision", p.getOrDefault("revision", 0));
                        return result;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero pipeline: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_list_pipeline_runs",
          description = "Lists runs of a specific pipeline with state and result")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listPipelineRuns(
            @ToolParam(description = "Pipeline ID") int pipelineId) {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/pipelines/" + pipelineId
                        + "/runs?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) {
                        return List.<Map<String, Object>>of();
                    }
                    List<Map<String, Object>> runs = (List<Map<String, Object>>) response.get("value");
                    return runs.stream().map(r -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", r.getOrDefault("id", 0));
                        result.put("name", r.getOrDefault("name", ""));
                        result.put("state", r.getOrDefault("state", ""));
                        result.put("result", r.getOrDefault("result", ""));
                        result.put("createdDate", r.getOrDefault("createdDate", ""));
                        result.put("finishedDate", r.getOrDefault("finishedDate", ""));
                        return result;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero run pipeline: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_trigger_pipeline",
          description = "Triggers a new run of an Azure DevOps pipeline, optionally on a specific branch")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> triggerPipeline(
            @ToolParam(description = "Pipeline ID to trigger") int pipelineId,
            @ToolParam(description = "Source branch, e.g. refs/heads/main (default: repository default branch)", required = false)
            String branch) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (branch != null && !branch.isBlank()) {
            String refName = branch.startsWith("refs/") ? branch : "refs/heads/" + branch;
            body.put("resources", Map.of(
                    "repositories", Map.of(
                            "self", Map.of("refName", refName)
                    )
            ));
        }

        return webClient.post()
                .uri(props.getBaseUrl() + "/_apis/pipelines/" + pipelineId
                        + "/runs?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore avvio pipeline " + pipelineId + ": " + e.getMessage())));
    }
}
