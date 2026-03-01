# MCP Azure DevOps Tools

Spring Boot starter providing 47 MCP tools for Azure DevOps. Covers work items (WIQL), Git repos, pipelines, boards, and releases via the Azure DevOps REST API.

## Installation

```xml
<dependency>
    <groupId>io.github.massimilianopili</groupId>
    <artifactId>mcp-devops-tools</artifactId>
    <version>0.1.0</version>
</dependency>
```

Requires Java 21+, Spring AI 1.0.0+, and [spring-ai-reactive-tools](https://github.com/MassimilianoPili/spring-ai-reactive-tools) 0.3.0+.

## Tools (47)

| Class | Count | Description |
|-------|-------|-------------|
| `DevOpsWorkItemTools` | 12 | WIQL queries, work item CRUD, sprint items, linking |
| `DevOpsGitTools` | 14 | Repos, branches, PRs, commits, file contents |
| `DevOpsPipelineTools` | 8 | List pipelines, runs, trigger builds |
| `DevOpsBoardTools` | 7 | Sprints, board columns, team iterations |
| `DevOpsReleaseTools` | 6 | Release analysis from work items |

## Configuration

```properties
# Required — enables all DevOps tools
MCP_DEVOPS_PAT=your-personal-access-token

# Azure DevOps coordinates
MCP_DEVOPS_ORG=your-organization
MCP_DEVOPS_PROJECT=your-project
MCP_DEVOPS_TEAM=your-team
```

## How It Works

- Uses `@ReactiveTool` ([spring-ai-reactive-tools](https://github.com/MassimilianoPili/spring-ai-reactive-tools)) for async `Mono<T>` methods
- Auto-configured via `DevOpsToolsAutoConfiguration` with `@ConditionalOnProperty(name = "mcp.devops.pat")`
- WebClient with Basic auth (`:PAT` base64-encoded)

## Requirements

- Java 21+
- Spring Boot 3.4+ with WebFlux
- Spring AI 1.0.0+
- spring-ai-reactive-tools 0.3.0+

## License

[MIT License](LICENSE)
