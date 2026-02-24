# MCP Azure DevOps Tools

Spring Boot starter that provides MCP tools for Azure DevOps (work items, repos, pipelines, boards, releases). Published on Maven Central as `io.github.massimilianopili:mcp-devops-tools`.

## Build Commands

```bash
# Build
/opt/maven/bin/mvn clean compile

# Install to local Maven repo (skip GPG for local)
/opt/maven/bin/mvn clean install -Dgpg.skip=true

# Deploy to Maven Central (requires GPG key + Central Portal credentials in ~/.m2/settings.xml)
/opt/maven/bin/mvn clean deploy
```

Java 17+ required. Maven is at `/opt/maven/bin/mvn` (not in PATH).

## Project Structure

```
src/main/java/io/github/massimilianopili/mcp/devops/
├── DevOpsProperties.java              # @ConfigurationProperties(prefix = "mcp.devops")
├── DevOpsConfig.java                  # WebClient bean (base URL + PAT auth)
├── DevOpsWorkItemTools.java           # @ReactiveTool: WIQL queries, work item CRUD, sprint items
├── DevOpsGitTools.java                # @ReactiveTool: repos, branches, PRs, repo files
├── DevOpsPipelineTools.java           # @ReactiveTool: list pipelines, runs, trigger
├── DevOpsBoardTools.java              # @ReactiveTool: sprints, board columns
├── DevOpsReleaseTools.java            # @ReactiveTool: release analysis from work items
└── DevOpsToolsAutoConfiguration.java  # Spring Boot auto-config

src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## Key Patterns

- **@ReactiveTool** (spring-ai-reactive-tools): Marks async methods returning `Mono<T>`. Auto-registered by `ReactiveToolAutoConfiguration` — no `ToolCallbackProvider` bean needed here.
- **Auto-configuration**: `DevOpsToolsAutoConfiguration` activates with `@ConditionalOnProperty(name = "mcp.devops.pat")` — setting the PAT enables all DevOps tools.
- **@EnableConfigurationProperties**: Binds `DevOpsProperties` without `@Component`.
- **WebClient**: All API calls use reactive `WebClient` with Basic auth (`:PAT` base64-encoded).

## Configuration

```properties
# Required — enables all DevOps tools
MCP_DEVOPS_PAT=your-personal-access-token

# Azure DevOps coordinates
MCP_DEVOPS_ORG=your-organization
MCP_DEVOPS_PROJECT=your-project
MCP_DEVOPS_TEAM=your-team
```

## Dependencies

- Spring Boot 3.4.1 (spring-boot-autoconfigure, spring-boot-starter-webflux)
- Spring AI 1.0.0 (spring-ai-model)
- spring-ai-reactive-tools 0.2.0

## Maven Central Publication

- GroupId: `io.github.massimilianopili`
- Plugin: `central-publishing-maven-plugin` v0.7.0
- Credentials: Central Portal token in `~/.m2/settings.xml` (server id: `central`)
