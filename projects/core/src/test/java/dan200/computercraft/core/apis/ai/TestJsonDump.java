package dan200.computercraft.core.apis.ai;

import dan200.computercraft.core.AiConfig;
import java.util.List;

public class TestJsonDump {
    public static void main(String[] args) {
        AiConfig.systemPrompt = "You are CC:Tweaked AI.";
        var model = new AiConfig.ModelEntry("test-model", "Test Model", 1000);
        var defaultOptions = new AiAPI.RequestOptions("test-model", 0.7f, 100, null, null, "Russian", false);
        var messages = List.of(new AiAPI.AiMessage("user", "Hello"));
        var json = AiRequestHandler.buildJsonBody(messages, model, defaultOptions);
        System.out.println(json);
    }
}
