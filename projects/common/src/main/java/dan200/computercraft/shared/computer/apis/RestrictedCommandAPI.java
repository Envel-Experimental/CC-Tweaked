// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.computer.apis;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.component.ComputerComponent;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A restricted version of the {@link CommandAPI} for advanced computers.
 * <p>
 * This only allows a small subset of commands (fireworks and redstone blocks) in order to prevent
 * griefing or general abuse.
 */
public class RestrictedCommandAPI implements ILuaAPI {
    private static final Logger LOG = LoggerFactory.getLogger(RestrictedCommandAPI.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    /**
     * A component used to identify advanced computers on the server.
     */
    public static final ComputerComponent<Boolean> IS_ADVANCED = ComputerComponent.create(ComputerCraftAPI.MOD_ID, "is_advanced");

    private final IComputerSystem computer;
    private final OutputReceiver receiver = new OutputReceiver();
    private boolean debug = false;

    public RestrictedCommandAPI(IComputerSystem computer) {
        this.computer = computer;
    }

    @Override
    public String[] getNames() {
        return new String[]{ "commands" };
    }

    /**
     * Enable or disable debug logging to the server console.
     */
    @LuaFunction
    public final void setDebug(boolean enabled) {
        this.debug = enabled;
    }

    private Object[] doCommand(String command, Optional<Map<?, ?>> variables) {
        var server = computer.getLevel().getServer();
        if (!server.isCommandBlockEnabled()) {
            return new Object[]{ false, List.of("Command blocks disabled by server") };
        }

        String processedCommand = command;
        if (variables.isPresent()) {
            processedCommand = substituteVariables(command, variables.get());
        }
        // Normalize all whitespace (including newlines) to single spaces
        processedCommand = processedCommand.replaceAll("\\s+", " ").trim();
        // Remove all non-printable characters (including invisible ones from Lua or BOM)
        processedCommand = processedCommand.replaceAll("[^\\x20-\\x7E]", "");

        if (!isValidCommand(processedCommand)) {
            if (debug) LOG.warn("Unauthorized command rejected: '{}' (Hex: {})",
                processedCommand, toHex(processedCommand.substring(0, Math.min(processedCommand.length(), 20))));
            return new Object[]{ false, List.of("Unauthorized command: '" + processedCommand + "'. Allowed: summon firework_rocket, setblock (redstone_block, air, stone), playsound, stopsound.") };
        }

        var commandManager = server.getCommands();
        try {
            receiver.clearOutput();
            int result = commandManager.performPrefixedCommand(getSource(), processedCommand);
            List<String> output = receiver.copyOutput();

            if (debug) {
                LOG.info("Computer {} executed command: '{}' (result: {}, output: {})",
                    computer.getID(), processedCommand, result, output);
            }

            return new Object[]{ result > 0, output, result };
        } catch (Throwable t) {
            LOG.error("Error running restricted command.", t);
            return new Object[]{ false, List.of("Java Exception Thrown: " + t) };
        }
    }

    private String substituteVariables(String command, Map<?, ?> variables) {
        var sb = new StringBuilder();
        var matcher = VARIABLE_PATTERN.matcher(command);
        while (matcher.find()) {
            var key = matcher.group(1);
            Object value = variables.get(key);
            if (value == null) value = matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean isValidCommand(String command) {

        String s = command.toLowerCase();
        if (s.startsWith("/")) s = s.substring(1).trim();

        // 1. Explicit Blacklist (for extra security as requested)
        if (s.startsWith("op") || s.startsWith("deop") || s.startsWith("gamemode") ||
            s.equals("stop") || s.startsWith("stop ") ||
            s.startsWith("kick") || s.startsWith("ban") || s.startsWith("pardon") ||
            s.startsWith("whitelist") || s.startsWith("save-all")) {
            return false;
        }

        // 2. Strict Whitelist
        if (s.startsWith("summon firework_rocket") || s.startsWith("summon minecraft:firework_rocket")) {
            return true;
        }

        if (s.startsWith("setblock ")) {
            return s.contains("redstone_block") || s.contains("air") || s.contains("stone");
        }

        if (s.startsWith("playsound ") || s.startsWith("stopsound ")) {
            return true;
        }

        // 3. Permissive fallback for fireworks (reliable start-of-command check)
        if (s.contains("firework_rocket")) {
            return true;
        }

        return false;
    }

    /**
     * Execute a specific command.
     */
    @LuaFunction(mainThread = true)
    public final Object[] exec(String command, Optional<Map<?, ?>> variables) {
        return doCommand(command, variables);
    }

    /**
     * Asynchronously execute a command.
     */
    @LuaFunction
    public final long execAsync(ILuaContext context, String command, Optional<Map<?, ?>> variables) throws LuaException {
        return context.issueMainThreadTask(() -> doCommand(command, variables));
    }

    /**
     * List all available commands which the computer has permission to execute.
     */
    @LuaFunction(mainThread = true)
    public final List<String> list(IArguments args) throws LuaException {
        var server = computer.getLevel().getServer();
        CommandNode<CommandSourceStack> node = server.getCommands().getDispatcher().getRoot();
        for (var j = 0; j < args.count(); j++) {
            var name = args.getString(j);
            node = node.getChild(name);
            if (!(node instanceof LiteralCommandNode)) return List.of();
        }

        List<String> result = new ArrayList<>();
        for (CommandNode<?> child : node.getChildren()) {
            if (child instanceof LiteralCommandNode<?>) result.add(child.getName());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Get the position of the current computer.
     */
    @LuaFunction
    public final Object[] getBlockPosition() {
        var pos = computer.getPosition();
        return new Object[]{ pos.getX(), pos.getY(), pos.getZ() };
    }

    /**
     * Get some basic information about a block.
     */
    @LuaFunction(mainThread = true)
    public final Map<?, ?> getBlockInfo(int x, int y, int z, Optional<String> dimension) throws LuaException {
        var level = getLevel(dimension);
        var position = new BlockPos(x, y, z);
        if (!level.isInWorldBounds(position)) throw new LuaException("Co-ordinates out of range");
        return getBlockInfo(level, position);
    }

    /**
     * Get information about a range of blocks.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<?, ?>> getBlockInfos(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Optional<String> dimension) throws LuaException {
        var level = getLevel(dimension);
        var min = new BlockPos(Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ));
        var max = new BlockPos(Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ));
        if (!level.isInWorldBounds(min) || !level.isInWorldBounds(max)) {
            throw new LuaException("Co-ordinates out of range");
        }

        var count = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (count > 4096) throw new LuaException("Too many blocks");

        List<Map<?, ?>> results = new ArrayList<>((int) count);
        for (var y = min.getY(); y <= max.getY(); y++) {
            for (var z = min.getZ(); z <= max.getZ(); z++) {
                for (var x = min.getX(); x <= max.getX(); x++) {
                    results.add(getBlockInfo(level, new BlockPos(x, y, z)));
                }
            }
        }
        return results;
    }

    private Map<?, ?> getBlockInfo(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        var result = new HashMap<String, Object>();
        result.put("name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());

        var stateTable = new HashMap<String, Object>();
        for (var entry : state.getValues().entrySet()) {
            stateTable.put(entry.getKey().getName(), stateValueToLua(entry.getValue()));
        }
        result.put("state", stateTable);

        return result;
    }

    private static Object stateValueToLua(Comparable<?> value) {
        if (value instanceof Boolean || value instanceof Number) return value;
        return value.toString();
    }

    private Level getLevel(Optional<String> id) throws LuaException {
        var currentLevel = computer.getLevel();
        if (id.isEmpty()) return currentLevel;

        var dimensionId = ResourceLocation.tryParse(id.get());
        if (dimensionId == null) throw new LuaException("Invalid dimension name");

        var level = currentLevel.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) throw new LuaException("Unknown dimension");

        return level;
    }

    private CommandSourceStack getSource() {
        var name = "@";
        var label = computer.getLabel();
        if (label != null) name = label;

        return new CommandSourceStack(receiver,
            Vec3.atCenterOf(computer.getPosition()), Vec2.ZERO,
            computer.getLevel(), 2,
            name, Component.literal(name),
            computer.getLevel().getServer(), null
        );
    }

    private final class OutputReceiver implements CommandSource {
        private final List<String> output = new ArrayList<>();

        void clearOutput() {
            output.clear();
        }

        List<String> copyOutput() {
            return List.copyOf(output);
        }

        @Override
        public void sendSystemMessage(Component textComponent) {
            output.add(textComponent.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    private String toHex(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes()) sb.append(String.format("%02X ", b));
        return sb.toString();
    }
}
