
# Specification: MCP Greeting Server (Quarkus + JBang)

## Overview

Build a production-ready **single-file Java MCP server** using [Quarkus MCP Server](https://quarkus.io/blog/mcp-server/) and JBang. The server exposes tools that an LLM host can invoke via the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/).

---

## Goals

- Deliver a runnable MCP server as a **single `.java` file** executable with JBang.
- Expose MCP tools callable by LLM hosts (for example, VS Code Copilot Chat).
- Keep MCP stdio transport clean by routing logs to file output.
- Support startup options through Picocli without shutting down the MCP server.

---

## Phases

| Phase | Scope                                         |
|-------|-----------------------------------------------|
| 1     | MCP server working end-to-end (stdio + tools) |
| 2     | Startup argument support via Picocli |

---

## Tech Stack

| Concern         | Technology                                        |
|-----------------|---------------------------------------------------|
| Runtime         | Java 17+, JBang                                   |
| Framework       | Quarkus (`quarkus-bom`)                           |
| MCP transport   | `quarkus-mcp-server-stdio` (subprocess/stdio)     |
| CLI             | Picocli via `quarkus-picocli` |
| Testing         | MCP Inspector (`@modelcontextprotocol/inspector`) |
| MCP host        | VS Code (GitHub Copilot / MCP extension)          |

---

## Implementation

### File: `McpGreetingServer.java`

The script header defines Java, dependencies, and runtime configuration:

```java
///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.18.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.1.0
//DEPS io.quarkus:quarkus-picocli
//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
//Q:CONFIG quarkus.log.file.enable=true
//Q:CONFIG quarkus.log.file.path=mcp-greeting-server.log
```

> Log output **must** be redirected to file because `stdio` is reserved for MCP JSON-RPC messages. Any output written to `stdout`/`stderr` can corrupt protocol traffic.

### Tool Implementation

Annotate a CDI bean method with `@Tool` and its parameters with `@ToolArg`:

```java
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class GreetingTools {

    @Tool(description = "Returns a personalized greeting message.")
    String greet(@ToolArg(description = "Name of the person to greet") String name) {
      return String.format("%s, %s! Welcome to the MCP Greeting Server.", ServerSettings.getGreetingPrefix(), name);
    }
}
```

The MCP framework automatically registers all `@Tool`-annotated methods and handles the JSON-RPC protocol.

---

## Running the Server

Execute directly with JBang:

```bash
jbang McpGreetingServer.java
```

Or make it executable:

```bash
chmod +x McpGreetingServer.java
./McpGreetingServer.java
```

---

## Testing

### Option 1 — MCP Inspector (recommended for development)

The [MCP Inspector](https://github.com/modelcontextprotocol/inspector) is a browser-based tool for interactively testing MCP servers.

**Requirements:** Node.js 18+

```bash
npx @modelcontextprotocol/inspector jbang McpGreetingServer.java
```

This starts a local web UI (usually at `http://localhost:5173`) where you can:

1. Browse the list of registered tools.
2. Invoke a tool with custom arguments.
3. Inspect raw JSON-RPC request/response messages.

### Option 2 — VS Code MCP Integration

VS Code with the GitHub Copilot extension supports MCP servers natively.

1. Open (or create) `.vscode/mcp.json` in your workspace:

```json
{
  "servers": {
    "mcp-greeting-server": {
      "type": "stdio",
      "command": "jbang",
      "args": ["--quiet", "${workspaceFolder}/McpGreetingServer.java"]
    }
  }
}
```

2. Reload VS Code. The MCP server should appear in the Copilot Chat tool picker.
3. Open Copilot Chat and ask something that triggers the tool, e.g.:  
   *"Use the greet tool to say hello to Alice."*

---

## Picocli Integration

### Why it's non-trivial

Picocli is useful for startup flags, but there are constraints when combined with stdio MCP transport:

| Conflict | Detail |
|----------|--------|
| **`stdin` ownership** | `quarkus-mcp-server-stdio` reads JSON-RPC messages from `stdin` in a loop. Picocli interactive prompts also read `stdin`. They will collide. |
| **Quarkus lifecycle** | If `run()` returns immediately, the process can exit and stop MCP handling. Keep the server alive with `Quarkus.waitForExit()`. |

### When Picocli is safe to add (Phase 2)

Picocli is safe **only** for parsing JVM startup arguments (flags passed on the command line before the server starts listening). The `run()` method must **not** return normally — it must block until Quarkus is ready to exit:

```java
@CommandLine.Command
public class McpGreetingServer implements Runnable {

    @CommandLine.Option(names = "--greeting", defaultValue = "Hello",
        description = "Greeting prefix used by the greet tool")
    String greeting;

    @Override
    public void run() {
        ServerSettings.setGreetingPrefix(greeting);
        Quarkus.waitForExit();
    }
}
```

With `Quarkus.waitForExit()` in `run()`, Quarkus remains active and the MCP server stays available for tool calls.

### Rules for Phase 2

- Only use `@CommandLine.Option` / `@CommandLine.Parameters` to read **startup args**.
- Never read from `stdin` inside the Picocli command.
- Always end `run()` with `Quarkus.waitForExit()`.
- Keep CLI options focused on startup configuration only.

---

## Acceptance Criteria

**Phase 1**
- [ ] `jbang McpGreetingServer.java` starts without errors.
- [ ] MCP Inspector lists the `greet` tool and returns the expected response when invoked.
- [ ] VS Code Copilot Chat can discover and call `greet` via `.vscode/mcp.json`.
- [ ] No output leaks to `stdout`/`stderr` (all logs go to `mcp-greeting-server.log`).

**Phase 2 (Picocli)**
- [ ] Adding `--greeting "Hi"` changes the `greet` tool prefix.
- [ ] Server continues running and responding to MCP calls after Picocli parses args.
- [ ] MCP Inspector still works after Picocli is added.

---

## References

- [Quarkus MCP Server blog post](https://quarkus.io/blog/mcp-server/)
- [Quarkus MCP Server extension guide](https://docs.quarkiverse.io/quarkus-mcp-server/dev/)
- [Model Context Protocol documentation](https://modelcontextprotocol.io/)
- [MCP Inspector (GitHub)](https://github.com/modelcontextprotocol/inspector)
- [VS Code MCP documentation](https://code.visualstudio.com/docs/copilot/chat/mcp-servers)

---

## Repository and Publishing

Recommended repository name: `mcp-greeting-server`.

Publish with authenticated GitHub CLI:

```bash
git init
git add .
git commit -m "feat: initialize MCP greeting server"
gh repo create mcp-greeting-server --public --source=. --remote=origin --push
```
