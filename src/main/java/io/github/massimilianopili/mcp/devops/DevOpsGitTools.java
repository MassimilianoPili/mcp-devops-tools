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
public class DevOpsGitTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsGitTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_list_repos",
          description = "Lists all Git repositories in the Azure DevOps project")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listRepos() {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/git/repositories?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) {
                        return List.<Map<String, Object>>of();
                    }
                    List<Map<String, Object>> repos = (List<Map<String, Object>>) response.get("value");
                    return repos.stream().map(r -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", r.getOrDefault("id", ""));
                        result.put("name", r.getOrDefault("name", ""));
                        result.put("defaultBranch", r.getOrDefault("defaultBranch", ""));
                        result.put("webUrl", r.getOrDefault("webUrl", ""));
                        result.put("size", r.getOrDefault("size", 0));
                        return result;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero repository: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_list_branches",
          description = "Lists branches of an Azure DevOps Git repository")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listBranches(
            @ToolParam(description = "Repository ID or name") String repoId) {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/git/repositories/" + repoId
                        + "/refs?filter=heads/&api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) {
                        return List.<Map<String, Object>>of();
                    }
                    List<Map<String, Object>> refs = (List<Map<String, Object>>) response.get("value");
                    return refs.stream().map(r -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        String name = (String) r.getOrDefault("name", "");
                        result.put("name", name.replace("refs/heads/", ""));
                        result.put("objectId", r.getOrDefault("objectId", ""));
                        return result;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero branch: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_list_pull_requests",
          description = "Lists pull requests of a repository, with optional status filter (active, completed, abandoned, all)")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listPullRequests(
            @ToolParam(description = "Repository ID or name") String repoId,
            @ToolParam(description = "Status filter: active, completed, abandoned, all (default: active)", required = false)
            String status) {
        String uri = props.getBaseUrl() + "/_apis/git/repositories/" + repoId
                + "/pullrequests?api-version=" + props.getApiVersion();
        if (status != null && !status.isBlank()) {
            uri += "&searchCriteria.status=" + status;
        }

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) {
                        return List.<Map<String, Object>>of();
                    }
                    List<Map<String, Object>> prs = (List<Map<String, Object>>) response.get("value");
                    return prs.stream().map(pr -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("pullRequestId", pr.getOrDefault("pullRequestId", 0));
                        result.put("title", pr.getOrDefault("title", ""));
                        result.put("status", pr.getOrDefault("status", ""));
                        result.put("sourceRefName", pr.getOrDefault("sourceRefName", ""));
                        result.put("targetRefName", pr.getOrDefault("targetRefName", ""));
                        result.put("creationDate", pr.getOrDefault("creationDate", ""));
                        result.put("isDraft", pr.getOrDefault("isDraft", false));
                        Object createdBy = pr.get("createdBy");
                        if (createdBy instanceof Map) {
                            result.put("createdBy", ((Map<String, Object>) createdBy).getOrDefault("displayName", ""));
                        }
                        return result;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero pull request: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_get_pull_request",
          description = "Retrieves full details of a specific pull request")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getPullRequest(
            @ToolParam(description = "Repository ID or name") String repoId,
            @ToolParam(description = "Pull request ID") int pullRequestId) {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/git/repositories/" + repoId
                        + "/pullrequests/" + pullRequestId
                        + "?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero PR #" + pullRequestId + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_list_repo_files",
          description = "Lists files in a Git repository, optionally filtered by branch and path")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listRepoFiles(
            @ToolParam(description = "Repository ID or name") String repoId,
            @ToolParam(description = "Path in the repository, e.g. /src/main", required = false) String scopePath,
            @ToolParam(description = "Branch name, e.g. main, develop", required = false) String branch,
            @ToolParam(description = "Recursion level: OneLevel, Full (default: OneLevel)", required = false) String recursionLevel) {
        StringBuilder uri = new StringBuilder();
        uri.append(props.getBaseUrl())
           .append("/_apis/git/repositories/").append(repoId)
           .append("/items?api-version=").append(props.getApiVersion());

        if (scopePath != null && !scopePath.isBlank()) {
            uri.append("&scopePath=").append(scopePath);
        }
        if (branch != null && !branch.isBlank()) {
            uri.append("&versionDescriptor.version=").append(branch)
               .append("&versionDescriptor.versionType=branch");
        }
        uri.append("&recursionLevel=").append(
                recursionLevel != null && !recursionLevel.isBlank() ? recursionLevel : "OneLevel");

        return webClient.get()
                .uri(uri.toString())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) {
                        return List.<Map<String, Object>>of();
                    }
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("value");
                    return items.stream().map(item -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("path", item.getOrDefault("path", ""));
                        result.put("isFolder", item.getOrDefault("isFolder", false));
                        result.put("gitObjectType", item.getOrDefault("gitObjectType", ""));
                        return result;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero file repository: " + e.getMessage()))));
    }
}
