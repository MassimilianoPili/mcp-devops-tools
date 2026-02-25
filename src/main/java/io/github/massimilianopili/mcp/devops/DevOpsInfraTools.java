package io.github.massimilianopili.mcp.devops;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@ConditionalOnProperty(name = "mcp.devops.pat")
public class DevOpsInfraTools {

    private final WebClient webClient;
    private final DevOpsProperties props;

    public DevOpsInfraTools(
            @Qualifier("devOpsWebClient") WebClient webClient,
            DevOpsProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "devops_list_agent_pools",
          description = "Elenca tutti gli agent pool disponibili nell'organizzazione Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listAgentPools() {
        return webClient.get()
                .uri(props.getOrgBaseUrl() + "/_apis/distributedtask/pools?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) return List.<Map<String, Object>>of();
                    List<Map<String, Object>> pools = (List<Map<String, Object>>) response.get("value");
                    return pools.stream().map(p -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", p.getOrDefault("id", ""));
                        r.put("name", p.getOrDefault("name", ""));
                        r.put("poolType", p.getOrDefault("poolType", ""));
                        r.put("size", p.getOrDefault("size", 0));
                        r.put("isHosted", p.getOrDefault("isHosted", false));
                        return r;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista agent pool: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_list_build_queues",
          description = "Elenca le code di build (agent queue) disponibili nel progetto Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listBuildQueues() {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/distributedtask/queues?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) return List.<Map<String, Object>>of();
                    List<Map<String, Object>> queues = (List<Map<String, Object>>) response.get("value");
                    return queues.stream().map(q -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", q.getOrDefault("id", ""));
                        r.put("name", q.getOrDefault("name", ""));
                        r.put("poolId", q.containsKey("pool")
                                ? ((Map<String, Object>) q.get("pool")).getOrDefault("id", "")
                                : "");
                        return r;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista build queue: " + e.getMessage()))));
    }

    @ReactiveTool(name = "devops_list_service_connections",
          description = "Elenca le service connection (endpoint di servizio) configurate nel progetto Azure DevOps")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listServiceConnections() {
        return webClient.get()
                .uri(props.getBaseUrl() + "/_apis/serviceendpoint/endpoints?api-version=" + props.getApiVersion())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (!response.containsKey("value")) return List.<Map<String, Object>>of();
                    List<Map<String, Object>> endpoints = (List<Map<String, Object>>) response.get("value");
                    return endpoints.stream().map(ep -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", ep.getOrDefault("id", ""));
                        r.put("name", ep.getOrDefault("name", ""));
                        r.put("type", ep.getOrDefault("type", ""));
                        r.put("url", ep.getOrDefault("url", ""));
                        r.put("isShared", ep.getOrDefault("isShared", false));
                        return r;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore lista service connection: " + e.getMessage()))));
    }
}
