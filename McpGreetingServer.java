///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//COMPILE_OPTIONS -encoding UTF-8
//RUNTIME_OPTIONS -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8

//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.18.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.1.0
//DEPS io.quarkus:quarkus-picocli

//Q:CONFIG quarkus.banner.enabled=false
// Logs must go to file — stdout/stderr are reserved for MCP JSON-RPC and any extra bytes corrupt the protocol.
//Q:CONFIG quarkus.log.level=INFO
//Q:CONFIG quarkus.log.file.enable=true
//Q:CONFIG quarkus.log.file.path=mcp-greeting-server.log
//Q:CONFIG quarkus.log.file.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3.}] %s%e%n

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "mcp-greeting-server",
    mixinStandardHelpOptions = true,
    description = "Runs a Quarkus MCP stdio server with greeting tools."
)
public class McpGreetingServer implements Runnable {

    private static final Logger LOG = Logger.getLogger(McpGreetingServer.class);

    @Option(
        names = "--greeting",
        defaultValue = "Hello",
        description = "Greeting prefix used by MCP greeting tools."
    )
    String greetingPrefix;

    @Override
    public void run() {
        LOG.infof("Starting MCP Greeting Server with greeting prefix: '%s'", greetingPrefix);
        ServerSettings.setGreetingPrefix(greetingPrefix);
        LOG.info("MCP Greeting Server ready — waiting for requests");
        // Must block here — returning would exit the process and stop MCP handling.
        Quarkus.waitForExit();
        LOG.info("MCP Greeting Server shutting down");
    }
}

@ApplicationScoped
class GreetingTools {

    private static final Logger LOG = Logger.getLogger(GreetingTools.class);

    @Tool(description = "Returns a personalized greeting message.")
    String greet(@ToolArg(description = "Name of the person to greet") String name) {
        LOG.infof("Tool 'greet' invoked with name='%s'", name);
        String result = String.format("%s, %s! Welcome to the MCP Greeting Server.", ServerSettings.getGreetingPrefix(), name);
        LOG.debugf("Tool 'greet' returning: %s", result);
        return result;
    }

    @Tool(description = "Returns a professional sign-off message.")
    String signoff(@ToolArg(description = "Recipient name") String name) {
        LOG.infof("Tool 'signoff' invoked with name='%s'", name);
        String result = String.format("Best regards, %s. - MCP Greeting Server", name);
        LOG.debugf("Tool 'signoff' returning: %s", result);
        return result;
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
