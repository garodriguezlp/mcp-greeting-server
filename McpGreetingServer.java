///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.18.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.1.0
//DEPS io.quarkus:quarkus-picocli

//Q:CONFIG quarkus.banner.enabled=false
// Logs must go to file — stdout/stderr are reserved for MCP JSON-RPC and any extra bytes corrupt the protocol.
//Q:CONFIG quarkus.log.level=WARN
//Q:CONFIG quarkus.log.file.enable=true
//Q:CONFIG quarkus.log.file.path=mcp-greeting-server.log

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "mcp-greeting-server",
    mixinStandardHelpOptions = true,
    description = "Runs a Quarkus MCP stdio server with greeting tools."
)
public class McpGreetingServer implements Runnable {

    @Option(
        names = "--greeting",
        defaultValue = "Hello",
        description = "Greeting prefix used by MCP greeting tools."
    )
    String greetingPrefix;

    @Override
    public void run() {
        ServerSettings.setGreetingPrefix(greetingPrefix);
        // Must block here — returning would exit the process and stop MCP handling.
        Quarkus.waitForExit();
    }
}

@ApplicationScoped
class GreetingTools {

    @Tool(description = "Returns a personalized greeting message.")
    String greet(@ToolArg(description = "Name of the person to greet") String name) {
        return String.format("%s, %s! Welcome to the MCP Greeting Server.", ServerSettings.getGreetingPrefix(), name);
    }

    @Tool(description = "Returns a professional sign-off message.")
    String signoff(@ToolArg(description = "Recipient name") String name) {
        return String.format("Best regards, %s. - MCP Greeting Server", name);
    }
}

final class ServerSettings {
    // volatile ensures the value written by Picocli's thread is visible to CDI bean threads.
    private static volatile String greetingPrefix = "Hello";

    private ServerSettings() {
    }

    static String getGreetingPrefix() {
        return greetingPrefix;
    }

    static void setGreetingPrefix(String value) {
        if (value != null && !value.isBlank()) {
            greetingPrefix = value;
        }
    }
}
