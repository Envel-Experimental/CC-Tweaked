import java.util.regex.Pattern;

public class RegexTest {
    private static final Pattern SUMMON_PATTERN = Pattern.compile("^/?summon\\s+(?:minecraft:)?firework_rocket(?:\\s+.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SETBLOCK_PATTERN = Pattern.compile("^/?setblock\\s+\\S+\\s+\\S+\\s+\\S+\\s+(?:minecraft:)?redstone_block(?:\\s+.*)?$", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) {
        // User's template after interpolation
        String testSummon = "summon firework_rocket 10.00 64.00 -20.00 {FireworksItem:{id:\"minecraft:firework_rocket\",Count:1,tag:{Fireworks:{Explosions:[{Type:1,Colors:[I;16711680],FadeColors:[I;16777215],Trail:1,Flicker:1}],Flight:2}}}}}";
        
        System.out.println("Testing SUMMON_PATTERN:");
        System.out.println("Input: " + testSummon);
        boolean summonMatches = SUMMON_PATTERN.matcher(testSummon).matches();
        System.out.println("Result: " + (summonMatches ? "MATCH" : "NO MATCH"));

        String testSetblock = "setblock ~ ~-1 ~ minecraft:redstone_block replace";
        System.out.println("\nTesting SETBLOCK_PATTERN:");
        System.out.println("Input: " + testSetblock);
        boolean setblockMatches = SETBLOCK_PATTERN.matcher(testSetblock).matches();
        System.out.println("Result: " + (setblockMatches ? "MATCH" : "NO MATCH"));

        if (summonMatches && setblockMatches) {
            System.out.println("\nVERIFICATION SUCCESSFUL");
        } else {
            System.out.println("\nVERIFICATION FAILED");
        }
    }
}
