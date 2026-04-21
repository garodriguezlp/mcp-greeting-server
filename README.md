# MCP Greeting Server

A professional, single-file Java MCP server powered by Quarkus and JBang.

## What it does

- Runs as an MCP stdio server compatible with LLM hosts.
- Exposes greeting tools (`greet`, `signoff`).
- Supports startup customization with `--greeting`.
- Routes logs to `mcp-greeting-server.log` to keep MCP stdio clean.

## Requirements

- Java 17+
- [JBang](https://www.jbang.dev/)
- Node.js 18+ (optional, for MCP Inspector)

## Run

```bash
jbang McpGreetingServer.java
```

Use a custom greeting prefix:

```bash
jbang McpGreetingServer.java --greeting "Hi"
```

## Test with MCP Inspector

```bash
npx @modelcontextprotocol/inspector jbang McpGreetingServer.java
```

## VS Code MCP configuration

Create `.vscode/mcp.json`:

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

## Publish to GitHub (authenticated `gh` CLI)

```bash
git init
git add .
git commit -m "feat: initialize MCP greeting server"
gh repo create mcp-greeting-server --public --source=. --remote=origin --push
```
