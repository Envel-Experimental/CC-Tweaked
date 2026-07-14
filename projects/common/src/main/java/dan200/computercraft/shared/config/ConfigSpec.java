// Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
//
// SPDX-License-Identifier: LicenseRef-CCPL

package dan200.computercraft.shared.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.core.CoreConfig;
import dan200.computercraft.core.Logging;
import dan200.computercraft.core.AiConfig;
import dan200.computercraft.core.apis.http.NetworkUtils;
import dan200.computercraft.core.apis.http.options.ProxyType;
import dan200.computercraft.core.computer.mainthread.MainThreadConfig;
import dan200.computercraft.shared.peripheral.monitor.MonitorRenderer;
import dan200.computercraft.shared.platform.PlatformHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.filter.MarkerFilter;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ConfigSpec {
    private static final int MODEM_MAX_RANGE = 100000;

    public static final ConfigFile serverSpec;

    public static final ConfigFile.Value<Integer> computerSpaceLimit;
    public static final ConfigFile.Value<Integer> floppySpaceLimit;
    public static final ConfigFile.Value<Integer> maximumFilesOpen;
    public static final ConfigFile.Value<String> defaultComputerSettings;
    public static final ConfigFile.Value<Boolean> logComputerErrors;
    public static final ConfigFile.Value<Boolean> commandRequireCreative;
    public static final ConfigFile.Value<Integer> uploadMaxSize;
    public static final ConfigFile.Value<List<? extends String>> disabledGenericMethods;

    public static final ConfigFile.Value<Integer> computerThreads;
    public static final ConfigFile.Value<Integer> maxMainGlobalTime;
    public static final ConfigFile.Value<Integer> maxMainComputerTime;

    public static final ConfigFile.Value<Boolean> httpEnabled;
    public static final ConfigFile.Value<Boolean> httpWebsocketEnabled;
    public static final ConfigFile.Value<List<? extends UnmodifiableConfig>> httpRules;

    public static final ConfigFile.Value<Integer> httpMaxRequests;
    public static final ConfigFile.Value<Integer> httpMaxWebsockets;

    public static final ConfigFile.Value<Integer> httpDownloadBandwidth;
    public static final ConfigFile.Value<Integer> httpUploadBandwidth;

    public static final ConfigFile.Value<ProxyType> httpProxyType;
    public static final ConfigFile.Value<String> httpProxyHost;
    public static final ConfigFile.Value<Integer> httpProxyPort;

    public static final ConfigFile.Value<Boolean> commandBlockEnabled;
    public static final ConfigFile.Value<Integer> modemRange;
    public static final ConfigFile.Value<Integer> modemHighAltitudeRange;
    public static final ConfigFile.Value<Integer> modemRangeDuringStorm;
    public static final ConfigFile.Value<Integer> modemHighAltitudeRangeDuringStorm;
    public static final ConfigFile.Value<Integer> maxNotesPerTick;
    public static final ConfigFile.Value<Integer> monitorBandwidth;

    public static final ConfigFile.Value<Boolean> turtlesNeedFuel;
    public static final ConfigFile.Value<Integer> turtleFuelLimit;
    public static final ConfigFile.Value<Integer> advancedTurtleFuelLimit;
    public static final ConfigFile.Value<Boolean> turtlesCanPush;

    public static final ConfigFile.Value<Integer> computerTermWidth;
    public static final ConfigFile.Value<Integer> computerTermHeight;

    public static final ConfigFile.Value<Integer> pocketTermWidth;
    public static final ConfigFile.Value<Integer> pocketTermHeight;

    public static final ConfigFile.Value<Integer> monitorWidth;
    public static final ConfigFile.Value<Integer> monitorHeight;

    public static final ConfigFile clientSpec;
    public static final ConfigFile.Value<MonitorRenderer> monitorRenderer;
    public static final ConfigFile.Value<Integer> monitorDistance;
    public static final ConfigFile.Value<Integer> uploadNagDelay;

    // --- AI fields ---
    private static ConfigFile.Value<Boolean> aiEnabled;
    private static ConfigFile.Value<String>  aiEndpoint;
    private static ConfigFile.Value<String>  aiApiKey;
    private static ConfigFile.Value<String>  aiDefaultModel;
    private static ConfigFile.Value<String>  aiDefaultLanguage;
    private static ConfigFile.Value<Boolean> aiAllowContext;
    private static ConfigFile.Value<Integer> aiMaxContextStringChars;
    private static ConfigFile.Value<String>  aiSystemPrompt;
    private static ConfigFile.Value<String>  aiErrorPrompt;
    private static ConfigFile.Value<Boolean> aiErrorHintEnabled;
    private static ConfigFile.Value<Boolean> aiRequireHttps;
    private static ConfigFile.Value<Integer> aiMaxContextTokens;
    private static ConfigFile.Value<Integer> aiMaxResponseTokens;
    private static ConfigFile.Value<Integer> aiRequestsPerMinute;
    private static ConfigFile.Value<Integer> aiRequestsPerHour;
    private static ConfigFile.Value<Integer> aiRequestsPerDay;
    private static ConfigFile.Value<Integer> aiMaxGlobalConcurrent;
    private static ConfigFile.Value<Integer> aiMaxMessageChars;
    private static ConfigFile.Value<Boolean> aiQueueOnLimit;
    private static ConfigFile.Value<Integer> aiQueueTimeoutSeconds;
    // Moderation sub-section
    private static ConfigFile.Value<Boolean> aiModEnabled;
    private static ConfigFile.Value<String>  aiModEndpoint;
    private static ConfigFile.Value<String>  aiModApiKey;
    private static ConfigFile.Value<String>  aiModFormat;
    private static ConfigFile.Value<String>  aiModChatPrompt;
    private static ConfigFile.Value<String>  aiModChatModel;
    private static ConfigFile.Value<Integer> aiModMaxRetries;
    private static ConfigFile.Value<Integer> aiModTimeoutSeconds;

    private static MarkerFilter logFilter = MarkerFilter.createFilter(Logging.COMPUTER_ERROR.getName(), Filter.Result.ACCEPT, Filter.Result.NEUTRAL);

    private ConfigSpec() {
    }

    static {
        if (LogManager.getContext(false) instanceof org.apache.logging.log4j.core.LoggerContext context) {
            context.addFilter(logFilter);
        }

        var builder = PlatformHelper.get().createConfigBuilder();

        { // General computers
            computerSpaceLimit = builder
                .comment("The disk space limit for computers and turtles, in bytes.")
                .define("computer_space_limit", Config.computerSpaceLimit);

            floppySpaceLimit = builder
                .comment("The disk space limit for floppy disks, in bytes.")
                .define("floppy_space_limit", Config.floppySpaceLimit);

            uploadMaxSize = builder
                .comment("""
                    The file upload size limit, in bytes. Must be in range of 1 KiB and 16 MiB.
                    Keep in mind that uploads are processed in a single tick - large files or
                    poor network performance can stall the networking thread. And mind the disk space!""")
                .defineInRange("upload_max_size", Config.uploadMaxSize, 1024, 16 * 1024 * 1024);

            maximumFilesOpen = builder
                .comment("Set how many files a computer can have open at the same time. Set to 0 for unlimited.")
                .defineInRange("maximum_open_files", CoreConfig.maximumFilesOpen, 0, Integer.MAX_VALUE);

            defaultComputerSettings = builder
                .comment("""
                    A comma separated list of default system settings to set on new computers.
                    Example: "shell.autocomplete=false,lua.autocomplete=false,edit.autocomplete=false"
                    will disable all autocompletion.""")
                .define("default_computer_settings", CoreConfig.defaultComputerSettings);

            logComputerErrors = builder
                .comment("""
                    Log exceptions thrown by peripherals and other Lua objects. This makes it easier
                    for mod authors to debug problems, but may result in log spam should people use
                    buggy methods.""")
                .define("log_computer_errors", true);

            commandRequireCreative = builder
                .comment("""
                    Require players to be in creative mode and be opped in order to interact with
                    command computers. This is the default behaviour for vanilla's Command blocks.""")
                .define("command_require_creative", Config.commandRequireCreative);

            disabledGenericMethods = builder
                .comment("""
                    A list of generic methods or method sources to disable. Generic methods are
                    methods added to a block/block entity when there is no explicit peripheral
                    provider. This includes inventory methods (i.e. inventory.getItemDetail,
                    inventory.pushItems), and (if on Forge), the fluid_storage and energy_storage
                    methods.
                    Methods in this list can either be a whole group of methods (computercraft:inventory)
                    or a single method (computercraft:inventory#pushItems).
                    """)
                .worldRestart()
                .defineList("disabled_generic_methods", List.of(), x -> x instanceof String);
        }

        {
            builder.comment("""
                Controls execution behaviour of computers. This is largely intended for
                fine-tuning servers, and generally shouldn't need to be touched.""");
            builder.push("execution");

            computerThreads = builder
                .comment("""
                    Set the number of threads computers can run on. A higher number means more
                    computers can run at once, but may induce lag. Please note that some mods may
                    not work with a thread count higher than 1. Use with caution.""")
                .worldRestart()
                .defineInRange("computer_threads", 1, 1, Integer.MAX_VALUE);

            maxMainGlobalTime = builder
                .comment("""
                    The maximum time that can be spent executing tasks in a single tick, in
                    milliseconds.
                    Note, we will quite possibly go over this limit, as there's no way to tell how
                    long a will take - this aims to be the upper bound of the average time.""")
                .defineInRange("max_main_global_time", (int) TimeUnit.NANOSECONDS.toMillis(MainThreadConfig.DEFAULT_MAX_GLOBAL_TIME), 1, Integer.MAX_VALUE);

            maxMainComputerTime = builder
                .comment("""
                    The ideal maximum time a computer can execute for in a tick, in milliseconds.
                    Note, we will quite possibly go over this limit, as there's no way to tell how
                    long a will take - this aims to be the upper bound of the average time.""")
                .defineInRange("max_main_computer_time", (int) TimeUnit.NANOSECONDS.toMillis(MainThreadConfig.DEFAULT_MAX_COMPUTER_TIME), 1, Integer.MAX_VALUE);

            builder.pop();
        }

        { // HTTP
            builder.comment("Controls the HTTP API");
            builder.push("http");

            httpEnabled = builder
                .comment("""
                    Enable the "http" API on Computers. Disabling this also disables the "pastebin" and
                    "wget" programs, that many users rely on. It's recommended to leave this on and use
                    the "rules" config option to impose more fine-grained control.""")
                .define("enabled", CoreConfig.httpEnabled);

            httpWebsocketEnabled = builder
                .comment("Enable use of http websockets. This requires the \"http_enable\" option to also be true.")
                .define("websocket_enabled", CoreConfig.httpWebsocketEnabled);

            httpRules = builder
                .comment("""
                    A list of rules which control behaviour of the "http" API for specific domains or
                    IPs. Each rule matches against a hostname and an optional port, and then sets several
                    properties for the request.  Rules are evaluated in order, meaning earlier rules override
                    later ones.

                    Valid properties:
                     - "host" (required): The domain or IP address this rule matches. This may be a domain name
                       ("pastebin.com"), wildcard ("*.pastebin.com") or CIDR notation ("127.0.0.0/8").
                     - "port" (optional): Only match requests for a specific port, such as 80 or 443.

                     - "action" (optional): Whether to allow or deny this request.
                     - "max_download" (optional): The maximum size (in bytes) that a computer can download in this
                       request.
                     - "max_upload" (optional): The maximum size (in bytes) that a computer can upload in a this request.
                     - "max_websocket_message" (optional): The maximum size (in bytes) that a computer can send or
                       receive in one websocket packet.
                     - "use_proxy" (optional): Enable use of the HTTP/SOCKS proxy if it is configured.""")
                .defineList("rules", AddressRuleConfig.defaultRules(), x -> x instanceof UnmodifiableConfig);

            httpMaxRequests = builder
                .comment("""
                    The number of http requests a computer can make at one time. Additional requests
                    will be queued, and sent when the running requests have finished. Set to 0 for
                    unlimited.""")
                .defineInRange("max_requests", CoreConfig.httpMaxRequests, 0, Integer.MAX_VALUE);

            httpMaxWebsockets = builder
                .comment("The number of websockets a computer can have open at one time.")
                .defineInRange("max_websockets", CoreConfig.httpMaxWebsockets, 1, Integer.MAX_VALUE);

            builder
                .comment("Limits bandwidth used by computers.")
                .push("bandwidth");

            httpDownloadBandwidth = builder
                .comment("The number of bytes which can be downloaded in a second. This is shared across all computers. (bytes/s).")
                .defineInRange("global_download", CoreConfig.httpDownloadBandwidth, 1, Integer.MAX_VALUE);

            httpUploadBandwidth = builder
                .comment("The number of bytes which can be uploaded in a second. This is shared across all computers. (bytes/s).")
                .defineInRange("global_upload", CoreConfig.httpUploadBandwidth, 1, Integer.MAX_VALUE);

            builder.pop();

            builder
                .comment("""
                    Tunnels HTTP and websocket requests through a proxy server. Only affects HTTP
                    rules with "use_proxy" set to true (off by default).
                    If authentication is required for the proxy, create a "computercraft-proxy.pw"
                    file in the same directory as "computercraft-server.toml", containing the
                    username and password separated by a colon, e.g. "myuser:mypassword". For
                    SOCKS4 proxies only the username is required.""")
                .push("proxy");

            httpProxyType = builder
                .comment("The type of proxy to use.")
                .defineEnum("type", CoreConfig.httpProxyType);

            httpProxyHost = builder
                .comment("The hostname or IP address of the proxy server.")
                .define("host", CoreConfig.httpProxyHost);

            httpProxyPort = builder
                .comment("The port of the proxy server.")
                .defineInRange("port", CoreConfig.httpProxyPort, 1, 65536);

            builder.pop();

            builder.pop();
        }

        { // Peripherals
            builder.comment("Various options relating to peripherals.");
            builder.push("peripheral");

            commandBlockEnabled = builder
                .comment("Enable Command Block peripheral support")
                .define("command_block_enabled", Config.enableCommandBlock);

            modemRange = builder
                .comment("The range of Wireless Modems at low altitude in clear weather, in meters.")
                .defineInRange("modem_range", Config.modemRange, 0, MODEM_MAX_RANGE);

            modemHighAltitudeRange = builder
                .comment("The range of Wireless Modems at maximum altitude in clear weather, in meters.")
                .defineInRange("modem_high_altitude_range", Config.modemHighAltitudeRange, 0, MODEM_MAX_RANGE);

            modemRangeDuringStorm = builder
                .comment("The range of Wireless Modems at low altitude in stormy weather, in meters.")
                .defineInRange("modem_range_during_storm", Config.modemRangeDuringStorm, 0, MODEM_MAX_RANGE);

            modemHighAltitudeRangeDuringStorm = builder
                .comment("The range of Wireless Modems at maximum altitude in stormy weather, in meters.")
                .defineInRange("modem_high_altitude_range_during_storm", Config.modemHighAltitudeRangeDuringStorm, 0, MODEM_MAX_RANGE);

            maxNotesPerTick = builder
                .comment("Maximum amount of notes a speaker can play at once.")
                .defineInRange("max_notes_per_tick", Config.maxNotesPerTick, 1, Integer.MAX_VALUE);

            monitorBandwidth = builder
                .comment("""
                    The limit to how much monitor data can be sent *per tick*. Note:
                     - Bandwidth is measured before compression, so the data sent to the client is
                       smaller.
                     - This ignores the number of players a packet is sent to. Updating a monitor for
                       one player consumes the same bandwidth limit as sending to 20.
                     - A full sized monitor sends ~25kb of data. So the default (1MB) allows for ~40
                       monitors to be updated in a single tick.
                    Set to 0 to disable.""")
                .defineInRange("monitor_bandwidth", (int) Config.monitorBandwidth, 0, Integer.MAX_VALUE);

            builder.pop();
        }

        { // Turtles
            builder.comment("Various options relating to turtles.");
            builder.push("turtle");

            turtlesNeedFuel = builder
                .comment("Set whether Turtles require fuel to move.")
                .define("need_fuel", Config.turtlesNeedFuel);

            turtleFuelLimit = builder
                .comment("The fuel limit for Turtles.")
                .defineInRange("normal_fuel_limit", Config.turtleFuelLimit, 0, Integer.MAX_VALUE);

            advancedTurtleFuelLimit = builder
                .comment("The fuel limit for Advanced Turtles.")
                .defineInRange("advanced_fuel_limit", Config.advancedTurtleFuelLimit, 0, Integer.MAX_VALUE);

            turtlesCanPush = builder
                .comment("""
                    If set to true, Turtles will push entities out of the way instead of stopping if
                    there is space to do so.""")
                .define("can_push", Config.turtlesCanPush);

            builder.pop();
        }

        {
            builder
                .comment("""
                    Configure the size of various computer's terminals.
                    Larger terminals require more bandwidth, so use with care.""")
                .push("term_sizes");

            builder.comment("Terminal size of computers.").push("computer");
            computerTermWidth = builder.comment("Width of computer terminal").defineInRange("width", Config.DEFAULT_COMPUTER_TERM_WIDTH, 1, 255);
            computerTermHeight = builder.comment("Height of computer terminal").defineInRange("height", Config.DEFAULT_COMPUTER_TERM_HEIGHT, 1, 255);
            builder.pop();

            builder.comment("Terminal size of pocket computers.").push("pocket_computer");
            pocketTermWidth = builder.comment("Width of pocket computer terminal").defineInRange("width", Config.DEFAULT_POCKET_TERM_WIDTH, 1, 255);
            pocketTermHeight = builder.comment("Height of pocket computer terminal").defineInRange("height", Config.DEFAULT_POCKET_TERM_HEIGHT, 1, 255);
            builder.pop();

            builder.comment("Maximum size of monitors (in blocks).").push("monitor");
            monitorWidth = builder.comment("Maximum width of monitors").defineInRange("width", Config.monitorWidth, 1, 32);
            monitorHeight = builder.comment("Maximum height of monitors").defineInRange("height", Config.monitorHeight, 1, 32);
            builder.pop();

        }

        { // AI API
            builder
                .comment("""
                    Configures the CC:Tweaked AI API.
                    The AI feature lets ComputerCraft programs call an OpenAI-compatible language
                    model via your own proxy server. The server API key is stored server-side only
                    and never transmitted to clients.""")
                .push("ai");

            aiEnabled               = builder.comment("Enable the AI API on computers.").define("enabled", AiConfig.enabled);
            aiEndpoint              = builder.comment("URL of your OpenAI-compatible proxy endpoint (e.g. https://myproxy.example.com).").define("endpoint", AiConfig.endpoint);
            aiApiKey                = builder.comment("API key for your proxy server. NEVER share this publicly.").define("api_key", "");
            aiDefaultModel          = builder.comment("Default model ID used when ai.ask() is called without a model option.").define("default_model", AiConfig.defaultModel);
            aiDefaultLanguage       = builder.comment("Default response language injected into {language} prompt placeholder.").define("default_response_language", AiConfig.defaultResponseLanguage);
            aiAllowContext          = builder.comment("Allow Lua programs to inject additional system context via opts.system_context.").define("allow_additional_system_context", AiConfig.allowAdditionalSystemContext);
            aiMaxContextStringChars = builder.comment("Maximum length of the system_context string from Lua.").defineInRange("max_context_string_chars", AiConfig.maxContextStringChars, 0, 2000);
            aiSystemPrompt          = builder.comment("System prompt prepended to every AI request. Supports {language} placeholder.").define("system_prompt", AiConfig.systemPrompt);
            aiErrorPrompt           = builder.comment("System prompt for the error-hint feature. Supports {language}.").define("error_assistant_prompt", AiConfig.errorAssistantPrompt);
            aiErrorHintEnabled      = builder.comment("Show an AI hint button in the computer GUI when a Lua error is detected.").define("error_hint_enabled", AiConfig.errorHintEnabled);
            aiRequireHttps          = builder.comment("Require the endpoint URL to use HTTPS.").define("require_https", AiConfig.requireHttps);
            aiMaxContextTokens      = builder.comment("Maximum number of tokens in the conversation context.").defineInRange("max_context_tokens", AiConfig.maxContextTokens, 64, 128000);
            aiMaxResponseTokens     = builder.comment("Maximum tokens in a single AI response.").defineInRange("max_response_tokens", AiConfig.maxResponseTokens, 16, 32768);
            aiRequestsPerMinute     = builder.comment("Max requests per player per minute (0 = unlimited).").defineInRange("requests_per_minute", AiConfig.requestsPerMinute, 0, Integer.MAX_VALUE);
            aiRequestsPerHour       = builder.comment("Max requests per player per hour (0 = unlimited).").defineInRange("requests_per_hour", AiConfig.requestsPerHour, 0, Integer.MAX_VALUE);
            aiRequestsPerDay        = builder.comment("Max requests per player per day (0 = unlimited).").defineInRange("requests_per_day", AiConfig.requestsPerDay, 0, Integer.MAX_VALUE);
            aiMaxGlobalConcurrent   = builder.comment("Max AI requests in flight simultaneously across all players (0 = unlimited).").defineInRange("max_global_concurrent", AiConfig.maxGlobalConcurrent, 0, Integer.MAX_VALUE);
            aiMaxMessageChars       = builder.comment("Maximum characters in a single message.").defineInRange("max_message_chars", AiConfig.maxMessageChars, 100, 32000);
            aiQueueOnLimit          = builder.comment("Queue requests that exceed rate limits instead of failing immediately.").define("queue_on_limit", AiConfig.queueOnLimit);
            aiQueueTimeoutSeconds   = builder.comment("Seconds to wait in queue before returning a rate-limit error.").defineInRange("queue_timeout_seconds", AiConfig.queueTimeoutSeconds, 1, 300);

            builder.comment("Response moderation/validation via your proxy server.").push("moderation");
            aiModEnabled       = builder.comment("Enable moderation of AI responses before delivery to the player.").define("enabled", AiConfig.validation.enabled);
            aiModEndpoint      = builder.comment("URL of your moderation proxy endpoint.").define("endpoint", AiConfig.validation.endpoint);
            aiModApiKey        = builder.comment("API key for the moderation endpoint.").define("api_key", "");
            aiModFormat        = builder.comment("Moderation format: CHAT or OPENAI_MODERATION.").define("format", AiConfig.validation.format.name());
            aiModChatPrompt    = builder.comment("Prompt sent to the moderation model in CHAT mode.").define("chat_prompt", AiConfig.validation.chatPrompt);
            aiModChatModel     = builder.comment("Model used in CHAT moderation mode.").define("chat_model", AiConfig.validation.chatModel);
            aiModMaxRetries    = builder.comment("Max re-generation attempts when moderation fails.").defineInRange("max_retries", AiConfig.validation.maxRetries, 1, 10);
            aiModTimeoutSeconds = builder.comment("Timeout in seconds for moderation requests.").defineInRange("timeout_seconds", AiConfig.validation.timeoutSeconds, 5, 120);
            builder.pop(); // moderation

            builder.pop(); // ai
        }

        serverSpec = builder.build(ConfigSpec::syncServer);

        var clientBuilder = PlatformHelper.get().createConfigBuilder();
        monitorRenderer = clientBuilder
            .comment("""
                The renderer to use for monitors. Generally this should be kept at "best" - if
                monitors have performance issues, you may wish to experiment with alternative
                renderers.""")
            .defineEnum("monitor_renderer", MonitorRenderer.BEST);
        monitorDistance = clientBuilder
            .comment("""
                The maximum distance monitors will render at. This defaults to the standard tile
                entity limit, but may be extended if you wish to build larger monitors.""")
            .defineInRange("monitor_distance", 64, 16, 1024);
        uploadNagDelay = clientBuilder
            .comment("The delay in seconds after which we'll notify about unhandled imports. Set to 0 to disable.")
            .defineInRange("upload_nag_delay", Config.uploadNagDelay, 0, 60);

        clientSpec = clientBuilder.build(ConfigSpec::syncClient);
    }

    public static void syncServer(@Nullable Path path) {
        // General
        Config.computerSpaceLimit = computerSpaceLimit.get();
        Config.floppySpaceLimit = floppySpaceLimit.get();
        Config.uploadMaxSize = uploadMaxSize.get();
        CoreConfig.maximumFilesOpen = maximumFilesOpen.get();
        CoreConfig.defaultComputerSettings = defaultComputerSettings.get();
        Config.commandRequireCreative = commandRequireCreative.get();

        // Update our log filter if needed.
        var logFilter = MarkerFilter.createFilter(
            Logging.COMPUTER_ERROR.getName(),
            logComputerErrors.get() ? Filter.Result.ACCEPT : Filter.Result.DENY,
            Filter.Result.NEUTRAL
        );
        if (!logFilter.equals(ConfigSpec.logFilter) && LogManager.getContext(false) instanceof org.apache.logging.log4j.core.LoggerContext context) {
            context.removeFilter(ConfigSpec.logFilter);
            context.addFilter(ConfigSpec.logFilter = logFilter);
        }

        // HTTP
        CoreConfig.httpEnabled = httpEnabled.get();
        CoreConfig.httpWebsocketEnabled = httpWebsocketEnabled.get();

        CoreConfig.httpRules = httpRules.get().stream().map(AddressRuleConfig::parseRule).toList();

        CoreConfig.httpMaxRequests = httpMaxRequests.get();
        CoreConfig.httpMaxWebsockets = httpMaxWebsockets.get();
        CoreConfig.httpDownloadBandwidth = httpDownloadBandwidth.get();
        CoreConfig.httpUploadBandwidth = httpUploadBandwidth.get();

        CoreConfig.httpProxyType = httpProxyType.get();
        CoreConfig.httpProxyHost = httpProxyHost.get();
        CoreConfig.httpProxyPort = httpProxyPort.get();

        if (path != null) ProxyPasswordConfig.init(path.resolveSibling(ComputerCraftAPI.MOD_ID + "-proxy.pw"));

        NetworkUtils.reloadConfig();

        // Peripheral
        Config.enableCommandBlock = commandBlockEnabled.get();
        Config.maxNotesPerTick = maxNotesPerTick.get();
        Config.modemRange = modemRange.get();
        Config.modemHighAltitudeRange = modemHighAltitudeRange.get();
        Config.modemRangeDuringStorm = modemRangeDuringStorm.get();
        Config.modemHighAltitudeRangeDuringStorm = modemHighAltitudeRangeDuringStorm.get();
        Config.monitorBandwidth = monitorBandwidth.get();

        // Turtles
        Config.turtlesNeedFuel = turtlesNeedFuel.get();
        Config.turtleFuelLimit = turtleFuelLimit.get();
        Config.advancedTurtleFuelLimit = advancedTurtleFuelLimit.get();
        Config.turtlesCanPush = turtlesCanPush.get();

        // Terminal size
        Config.monitorWidth = monitorWidth.get();
        Config.monitorHeight = monitorHeight.get();

        // AI API
        if (aiEnabled != null) {
            AiConfig.enabled                    = aiEnabled.get();
            AiConfig.endpoint                   = aiEndpoint.get();
            AiConfig.serverApiKey               = aiApiKey.get();
            AiConfig.defaultModel               = aiDefaultModel.get();
            AiConfig.defaultResponseLanguage    = aiDefaultLanguage.get();
            AiConfig.allowAdditionalSystemContext = aiAllowContext.get();
            AiConfig.maxContextStringChars      = aiMaxContextStringChars.get();
            AiConfig.systemPrompt               = aiSystemPrompt.get();
            AiConfig.errorAssistantPrompt       = aiErrorPrompt.get();
            AiConfig.errorHintEnabled           = aiErrorHintEnabled.get();
            AiConfig.requireHttps               = aiRequireHttps.get();
            AiConfig.maxContextTokens           = aiMaxContextTokens.get();
            AiConfig.maxResponseTokens          = aiMaxResponseTokens.get();
            AiConfig.requestsPerMinute          = aiRequestsPerMinute.get();
            AiConfig.requestsPerHour            = aiRequestsPerHour.get();
            AiConfig.requestsPerDay             = aiRequestsPerDay.get();
            AiConfig.maxGlobalConcurrent        = aiMaxGlobalConcurrent.get();
            AiConfig.maxMessageChars            = aiMaxMessageChars.get();
            AiConfig.queueOnLimit               = aiQueueOnLimit.get();
            AiConfig.queueTimeoutSeconds        = aiQueueTimeoutSeconds.get();

            AiConfig.validation.enabled         = aiModEnabled.get();
            AiConfig.validation.endpoint        = aiModEndpoint.get();
            AiConfig.validation.apiKey          = aiModApiKey.get();
            AiConfig.validation.chatPrompt      = aiModChatPrompt.get();
            AiConfig.validation.chatModel       = aiModChatModel.get();
            AiConfig.validation.maxRetries      = aiModMaxRetries.get();
            AiConfig.validation.timeoutSeconds  = aiModTimeoutSeconds.get();
            try {
                AiConfig.validation.format = AiConfig.ModerationFormat.valueOf(
                    aiModFormat.get().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}

            try { AiConfig.validate(); }
            catch (IllegalStateException e) {
                org.apache.logging.log4j.LogManager.getLogger(ConfigSpec.class)
                    .error("[AI] Config validation failed — AI disabled: {}", e.getMessage());
                AiConfig.enabled = false;
            }
        }
    }

    public static void syncClient(@Nullable Path path) {
        Config.monitorRenderer = monitorRenderer.get();
        Config.monitorDistance = monitorDistance.get();
        Config.uploadNagDelay = uploadNagDelay.get();
    }
}
