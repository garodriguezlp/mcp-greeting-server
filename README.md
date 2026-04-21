# MCP Greeting Server

A minimal, single-file Java MCP server you can run instantly with JBang. Built to help you understand how MCP servers work from the ground up.

## What is MCP?

The [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) is an open standard that lets AI assistants (like GitHub Copilot or Claude) call external tools and data sources. An MCP server exposes **tools** — functions the AI can invoke — over a simple JSON-RPC channel. This project uses **stdio** as the transport: the AI host launches the server as a subprocess and communicates through its standard input/output.

## What you'll learn

- How to write a working MCP server in a **single `.java` file**, no build system needed
- How **JBang** lets you declare dependencies in source comments and run Java like a script
- How **Quarkus** + the `quarkus-mcp-server-stdio` extension handles the MCP protocol automatically
- How to expose MCP tools with `@Tool` and `@ToolArg` annotations
- The **non-obvious conflict** between Picocli CLI arguments and stdio MCP transport — and how to solve it
- How to integrate an MCP server with **VS Code Copilot Chat**

## How it works

The server is three classes in one file:

| Class | Role |
|---|---|
| `McpGreetingServer` | Picocli command — parses CLI flags, then blocks with `Quarkus.waitForExit()` |
| `GreetingTools` | CDI bean — holds the `@Tool` methods the AI can call |
| `ServerSettings` | Shared state — thread-safe storage for the configurable greeting prefix |

Quarkus scans for `@Tool`-annotated methods at startup and registers them with the MCP framework automatically. The framework then handles all JSON-RPC messaging with the AI host.

## Tech stack

| Concern | Technology |
|---|---|
| Script runner | [JBang](https://www.jbang.dev/) |
| Runtime | Java 17+ |
| Framework | [Quarkus](https://quarkus.io/) |
| MCP transport | [`quarkus-mcp-server-stdio`](https://docs.quarkiverse.io/quarkus-mcp-server/dev/) |
| CLI parsing | [Picocli](https://picocli.info/) via `quarkus-picocli` |
| Interactive testing | [MCP Inspector](https://github.com/modelcontextprotocol/inspector) |

## Requirements

- Java 17+
- [JBang](https://www.jbang.dev/)
- Node.js 18+ (optional — only needed for MCP Inspector)

## Run

```bash
jbang McpGreetingServer.java
```

Use a custom greeting prefix:

```bash
jbang McpGreetingServer.java --greeting "Hi"
```

JBang downloads all dependencies on the first run and caches them. Subsequent starts are fast.

## Test with MCP Inspector

[MCP Inspector](https://github.com/modelcontextprotocol/inspector) is a browser-based tool for testing MCP servers interactively:

```bash
npx @modelcontextprotocol/inspector jbang McpGreetingServer.java
```

This opens a local web UI (usually at `http://localhost:5173`) where you can:

1. Browse the registered tools (`greet`, `signoff`)
2. Invoke a tool with custom arguments
3. Inspect the raw JSON-RPC request and response

This is the fastest way to verify your server works before connecting it to an AI host.

## Use with VS Code

VS Code with the GitHub Copilot extension supports MCP servers natively. The `.vscode/mcp.json` file in this repo is already configured:

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

Steps:

1. Open this folder in VS Code
2. Reload the window — VS Code picks up `mcp.json` automatically
3. Open Copilot Chat and ask something that triggers a tool, e.g.:
   > *"Use the greet tool to say hello to Alice."*

The `--quiet` flag suppresses JBang's progress output, which would otherwise corrupt the stdio channel (see below).

## Key concept: why logs must go to a file

MCP's stdio transport uses standard input and output as a **dedicated JSON-RPC channel**. Any extra bytes written to stdout or stderr — even a single log line — corrupt the protocol stream and cause the AI host to fail or disconnect.

That's why the JBang header redirects all logging to a file:

```java
//Q:CONFIG quarkus.log.level=WARN
//Q:CONFIG quarkus.log.file.enable=true
//Q:CONFIG quarkus.log.file.path=mcp-greeting-server.log
```

If something seems broken, check `mcp-greeting-server.log` first.

## Key concept: Picocli + stdio MCP

Picocli is useful for startup flags, but there's a subtle conflict with stdio MCP transport:

| Problem | Detail |
|---|---|
| `stdin` ownership | `quarkus-mcp-server-stdio` reads JSON-RPC messages from `stdin` in a loop. Picocli interactive prompts also read `stdin`. They collide. |
| Quarkus lifecycle | If `run()` returns immediately, the process exits and MCP handling stops. |

The solution is straightforward: only use `@CommandLine.Option` for startup flags (never read from stdin inside the command), and always end `run()` with `Quarkus.waitForExit()`:

```java
@Override
public void run() {
    ServerSettings.setGreetingPrefix(greetingPrefix); // apply startup flag
    Quarkus.waitForExit();                            // block until shutdown signal
}
```

This keeps Quarkus — and the MCP server — running while Picocli's work is done.

## Further reading

- [Quarkus MCP Server blog post](https://quarkus.io/blog/mcp-server/)
- [Quarkus MCP Server extension guide](https://docs.quarkiverse.io/quarkus-mcp-server/dev/)
- [Model Context Protocol documentation](https://modelcontextprotocol.io/)
- [MCP Inspector (GitHub)](https://github.com/modelcontextprotocol/inspector)
- [VS Code MCP documentation](https://code.visualstudio.com/docs/copilot/chat/mcp-servers)
- [JBang documentation](https://www.jbang.dev/documentation/)
