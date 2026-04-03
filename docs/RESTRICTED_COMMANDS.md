# Restricted Commands API

The `commands` API is now available on **Advanced Computers** (Gold) with restricted capabilities. This allows for safe administrative actions without full op permissions. 

> [!NOTE]
> Commands executed through this API are **silenced** by default. They will return their output to Lua but will **NOT** appear in the Minecraft global chat or command logs, preventing chat "flooding".

## Available Commands

Only the following commands are permitted:

1.  **Summon Fireworks**:
    - `summon firework_rocket <x> <y> <z> [nbt]`
    - *Example*: `commands.exec("summon firework_rocket ~ ~5 ~ {FireworksItem:{id:\"minecraft:firework_rocket\",Count:1}}")`
2.  **Set Blocks**:
    - `setblock <x> <y> <z> <block>` (Allowed blocks: `redstone_block`, `air`, `stone`)
    - *Example*: `commands.exec("setblock ~ ~-1 ~ minecraft:redstone_block")`
3.  **Play Sounds**:
    - `playsound <sound> <source> <targets> [pos] [volume] [pitch] [minVolume]`
    - *Example*: `commands.exec("playsound minecraft:music_disc.cat record @a")`
4.  **Stop Sounds**:
    - `stopsound <targets> [source] [sound]`
    - *Example*: `commands.exec("stopsound @a record minecraft:music_disc.cat")`

## Variable Substitution

The `exec` and `execAsync` functions accept an optional second argument: a table of variables to substitute into the command string. Use `${name}` syntax for placeholders.

### Example

```lua
local pos = { x = 10 , y = 64 , z = 10 }
commands.exec("setblock ${x} ${y} ${z} redstone_block" , pos)
```

## Advanced Firework Generator Example

You can create a flexible launcher that supports various types, colors, and effects using a Lua table.

```lua
local function launch(x , y , z , options)
    local template = "/summon firework_rocket ${x} ${y} ${z} " ..
        "{FireworksItem:{id:\"minecraft:firework_rocket\",Count:1,tag:{Fireworks:{" ..
        "Explosions:[{Type:${type},Colors:[I;${colors}],FadeColors:[I;${fade}],Trail:${trail},Flicker:${flicker}}]," ..
        "Flight:${flight}}}}}"

    local vars = {
        x = x , y = y , z = z ,
        type = options.type or 0 ,
        colors = table.concat(options.colors or { 16711680 } , ","),
        fade = table.concat(options.fade or { 16777215 } , ","),
        trail = options.trail and 1 or 0 ,
        flicker = options.flicker and 1 or 0 ,
        flight = options.flight or 2
    }

    return commands.exec(template , vars)
end

-- Example: Large Red & Green Ball with Trail
launch("~" , "~5" , "~" , {
    type = 1 , -- Large Ball
    colors = { 16711680 , 65280 } , -- Red, Green
    trail = true ,
    flight = 1
})

-- Example: Blue Star shaped with Flicker
launch("~" , "~5" , "~" , {
    type = 2 , -- Star
    colors = { 255 } , -- Blue
    flicker = true ,
    flight = 2
})
```

### Firework Types
- `0`: Small Ball
- `1`: Large Ball
- `2`: Star-shaped
- `3`: Creeper-shaped
- `4`: Burst

### Color Helper
Colors are integers. You can calculate them using `r * 65536 + g * 256 + b`.

## Cylindrical Firework Show Example

This script launches random fireworks within a specified cylinder radius and height variation.

```lua
local function launchRandomShow(cx , cy , cz , radius , hVar)
    local function randCol() return math.random(0 , 16777215) end

    for i = 1 , 5 do
        -- Pick a random point in a circle
        local angle = math.random() * math.pi * 2
        local r = math.sqrt(math.random()) * radius
        local x = cx + math.cos(angle) * r
        local z = cz + math.sin(angle) * r
        local y = cy + math.random(-hVar , hVar)

        launch(x , y , z , {
            type = math.random(0 , 4) ,
            colors = { randCol() , randCol() } ,
            fade = { randCol() } ,
            trail = math.random() > 0.5 ,
            flicker = math.random() > 0.3 ,
            flight = math.random(1 , 3)
        })
        os.sleep(0.2)
    end
end

-- Example: Launch a show around the computer with 10 block radius and +-3 height variation
local x , y , z = commands.getBlockPosition()
launchRandomShow(x , y + 10 , z , 10 , 3)
```

## Functions

### `commands.exec(command: string, variables?: table): boolean, list, number`
Executes a command and returns:
1.  **success**: Whether the command executed successfully.
2.  **output**: A list of output messages from the command.
3.  **result**: The number of affected objects/blocks.

### `commands.execAsync(command: string, variables?: table): number`
Asynchronously executes a command and returns a task ID. A `task_complete` event will be queued when finished.

### `commands.setDebug(enabled: boolean)`
Enables or disables logging of all executed commands and their results to the server console. Useful for debugging scripts.

## Notes
- This implementation is server-side only and does not require client-side updates.
- If an unauthorized command is provided, the function will return `false` with an error message.
