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
public class DevOpsRepoAdvancedTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsRepoAdvancedTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_create_repository",
          description = "Crea un nuovo repository Git nel progetto Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createRepository(
            @ToolParam(description = "Nome del nuovo repository") String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("project", Map.of("id", props.getProject()));

        return webClient.post()
                .uri(props.getBaseUrl() + "/_apis/git/repositories?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione repository: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_delete_repository",
          description = "Elimina un repository Git dal progetto Azure DevOps")
    public Mono<Map<String, Object>> deleteRepository(
            @ToolParam(description = "ID o nome del repository da eliminare") String repoId) {
        return webClient.delete()
                .uri(props.getBaseUrl() + "/_apis/git/repositories/" + repoId + "?api-version=" + props.getApiVersion())
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "deleted", true))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore eliminazione repository: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_get_commits",
          description = "Recupera i commit di un repository Git Azure DevOps, con filtro opzionale per path")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> getCommits(
            @ToolParam(description = "ID o nome del repository") String repoId,
            @ToolParam(description = "Numero massimo di commit da restituire (default: 20)", required = false) Integer top,
            @ToolParam(description = "Filtra per path file/directory, es: /src/main", required = false) String itemPath) {
        int limit = (top != null && top > 0) ? top : 20;
        String uri = props.getBaseUrl() + "/_apis/git/repositories/" + repoId
                + "/commits?$top=" + limit + "&api-version=" + props.getApiVersion();
        if (itemPath != null && !itemPath.isBlank()) {
            uri += "&searchCriteria.itemPath=" + itemPath;
        }

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) return List.<Map<String, Object>>of();
                    List<Map<String, Object>> commits = (List<Map<String, Object>>) response.get("value");
                    return commits.stream().map(c -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("commitId", c.getOrDefault("commitId", ""));
                        r.put("comment", c.getOrDefault("comment", ""));
                        r.put("committer", extractName(c, "committer"));
                        r.put("authorDate", extractDate(c, "committer"));
                        return r;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero commit: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_create_branch",
          description = "Crea un nuovo branch in un repository Git Azure DevOps a partire da un commit SHA")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createBranch(
            @ToolParam(description = "ID o nome del repository") String repoId,
            @ToolParam(description = "Nome del nuovo branch, es: feature/nuova-feature") String branchName,
            @ToolParam(description = "SHA del commit da cui creare il branch (objectId)") String sourceSha) {
        List<Map<String, String>> body = List.of(Map.of(
                "name", "refs/heads/" + branchName,
                "newObjectId", sourceSha,
                "oldObjectId", "0000000000000000000000000000000000000000"
        ));

        return webClient.post()
                .uri(props.getBaseUrl() + "/_apis/git/repositories/" + repoId + "/refs?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione branch: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_delete_branch",
          description = "Elimina un branch da un repository Git Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> deleteBranch(
            @ToolParam(description = "ID o nome del repository") String repoId,
            @ToolParam(description = "Nome del branch da eliminare, es: feature/vecchia-feature") String branchName,
            @ToolParam(description = "SHA corrente del branch (objectId)") String currentSha) {
        List<Map<String, String>> body = List.of(Map.of(
                "name", "refs/heads/" + branchName,
                "newObjectId", "0000000000000000000000000000000000000000",
                "oldObjectId", currentSha
        ));

        return webClient.post()
                .uri(props.getBaseUrl() + "/_apis/git/repositories/" + repoId + "/refs?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore eliminazione branch: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_create_pull_request",
          description = "Crea una nuova pull request in un repository Git Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createPullRequest(
            @ToolParam(description = "ID o nome del repository") String repoId,
            @ToolParam(description = "Titolo della pull request") String title,
            @ToolParam(description = "Branch sorgente (con prefisso refs/heads/, es: refs/heads/feature/x)") String sourceRefName,
            @ToolParam(description = "Branch destinazione (es: refs/heads/main)") String targetRefName,
            @ToolParam(description = "Descrizione della pull request", required = false) String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("sourceRefName", sourceRefName);
        body.put("targetRefName", targetRefName);
        body.put("description", description != null ? description : "");
        body.put("reviewers", List.of());

        return webClient.post()
                .uri(props.getBaseUrl() + "/_apis/git/repositories/" + repoId + "/pullrequests?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione pull request: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_complete_pull_request",
          description = "Completa (merge) una pull request in Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> completePullRequest(
            @ToolParam(description = "ID o nome del repository") String repoId,
            @ToolParam(description = "ID della pull request") int pullRequestId,
            @ToolParam(description = "Commit SHA dell'ultimo commit del branch sorgente") String lastSourceCommitId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "completed");
        body.put("lastMergeSourceCommit", Map.of("commitId", lastSourceCommitId));

        return webClient.patch()
                .uri(props.getBaseUrl() + "/_apis/git/repositories/" + repoId
                        + "/pullrequests/" + pullRequestId + "?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore completamento PR: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_abandon_pull_request",
          description = "Abbandona (chiude senza merge) una pull request in Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> abandonPullRequest(
            @ToolParam(description = "ID o nome del repository") String repoId,
            @ToolParam(description = "ID della pull request") int pullRequestId) {
        return webClient.patch()
                .uri(props.getBaseUrl() + "/_apis/git/repositories/" + repoId
                        + "/pullrequests/" + pullRequestId + "?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("status", "abandoned"))
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore abbandono PR: " + e.getMessage())));
    }

    @ReactiveTool(name = "devops_add_pr_comment",
          description = "Aggiunge un commento a una pull request in Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> addPrComment(
            @ToolParam(description = "ID o nome del repository") String repoId,
            @ToolParam(description = "ID della pull request") int pullRequestId,
            @ToolParam(description = "Testo del commento") String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("comments", List.of(Map.of("content", content, "commentType", 1)));
        body.put("status", 1);

        return webClient.post()
                .uri(props.getBaseUrl() + "/_apis/git/repositories/" + repoId
                        + "/pullrequests/" + pullRequestId + "/threads?api-version=" + props.getApiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore aggiunta commento PR: " + e.getMessage())));
    }

    @SuppressWarnings("unchecked")
    private String extractName(Map<String, Object> commit, String key) {
        Object obj = commit.get(key);
        if (obj instanceof Map) {
            return (String) ((Map<String, Object>) obj).getOrDefault("name", "");
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String extractDate(Map<String, Object> commit, String key) {
        Object obj = commit.get(key);
        if (obj instanceof Map) {
            return (String) ((Map<String, Object>) obj).getOrDefault("date", "");
        }
        return "";
    }
}
