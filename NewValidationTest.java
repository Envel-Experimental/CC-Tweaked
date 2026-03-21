public class NewValidationTest {
    public static void main(String[] args) {
        System.out.println("Testing NEW validation logic (No Regex):");
        
        test("summon firework_rocket ~ ~5 ~ {FireworksItem:{id:\"minecraft:firework_rocket\",Count:1}}");
        test("  summon    minecraft:firework_rocket  ~ ~5 ~   ");
        test("setblock ~ ~-1 ~ minecraft:redstone_block replace");
        test("setblock ~ ~-1 ~ air");
        test("setblock ~ ~-1 ~ stone");
        test("playsound minecraft:music_disc.cat record @a");
        test("stopsound @a record");
        
        System.out.println("\nTesting MULTI-LINE (should pass with normalization):");
        test("summon firework_rocket ~ ~ ~\n{\n  FireworksItem: { id: \"minecraft:firework_rocket\" }\n}");
        
        System.out.println("\nTesting FORBIDDEN:");
        test("summon firework_rocket ~ ~ ~; say hacked");
        test("setblock ~ ~ ~ tnt");
    }

    private static void test(String command) {
        String processed = command.replaceAll("\\s+", " ").trim();
        boolean valid = isValidCommand(processed);
        System.out.printf("Input: [%s]\nResult: %s\n\n", command.replace("\n", "\\n"), valid ? "MATCH" : "FAIL");
    }

    private static boolean isValidCommand(String s) {
        if (s.contains(";")) return false;

        if (s.startsWith("summon firework_rocket ") || s.startsWith("summon minecraft:firework_rocket ")) {
            return true;
        }

        if (s.startsWith("setblock ")) {
            return s.contains("redstone_block") || s.contains("air") || s.contains("stone");
        }

        if (s.startsWith("playsound ") || s.startsWith("stopsound ")) {
            return true;
        }

        return false;
    }
}
