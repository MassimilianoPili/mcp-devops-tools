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
public class DevOpsPolicyTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsPolicyTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_list_branch_policies",
          description = "Lists branch policies configured in the Azure DevOps project")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listBranchPolicies() {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/policy/configurations?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) return List.<Map<String, Object>>of();
                    List<Map<String, Object>> configs = (List<Map<String, Object>>) response.get("value");
                    return configs.stream().map(c -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", c.getOrDefault("id", ""));
                        r.put("isEnabled", c.getOrDefault("isEnabled", false));
                        r.put("isBlocking", c.getOrDefault("isBlocking", false));
                        r.put("type", c.containsKey("type")
                                ? ((Map<String, Object>) c.get("type")).getOrDefault("displayName", "")
                                : "");
                        return r;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista branch policy: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_create_branch_policy",
          description = "Creates a branch policy in Azure DevOps. Common type: fa2c2666-e2e5-4601-a3a8-0ac19db4921a (Minimum number of reviewers)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createBranchPolicy(
            @ToolParam(description = "Policy type ID (GUID), e.g. fa2c2666-e2e5-4601-a3a8-0ac19db4921a for minimum reviewers") String policyTypeId,
            @ToolParam(description = "Whether the policy is enabled") boolean isEnabled,
            @ToolParam(description = "Whether the policy is blocking (prevents merge if not satisfied)") boolean isBlocking,
            @ToolParam(description = "Branch name to apply the policy to, e.g. refs/heads/main") String refName,
            @ToolParam(description = "Repository ID to apply the policy to") String repositoryId,
            @ToolParam(description = "Minimum number of reviewers (for minimum reviewers policy)", required = false) Integer minimumApproverCount) {

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("refName", refName);
        scope.put("matchKind", "Exact");
        scope.put("repositoryId", repositoryId);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("scope", List.of(scope));
        if (minimumApproverCount != null) {
            settings.put("minimumApproverCount", minimumApproverCount);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("isEnabled", isEnabled);
        body.put("isBlocking", isBlocking);
        body.put("type", Map.of("id", policyTypeId));
        body.put("settings", settings);

        return webClient.post()
                .uri(props.getBaseUrl() + "/_apis/policy/configurations?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione branch policy: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_delete_branch_policy",
          description = "Deletes a branch policy from the Azure DevOps project")
    public Mono<Map<String, Object>> deleteBranchPolicy(
            @ToolParam(description = "Policy configuration ID to delete") int configId) {
        return webClient.delete()
                .uri(props.getBaseUrl() + "/_apis/policy/configurations/" + configId + "?api-version=" + props.getApiVersion())
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "deleted", true, "configId", configId))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore eliminazione branch policy: " + e.getMessage())));
    }
}
