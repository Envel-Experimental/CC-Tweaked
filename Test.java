import java.nio.charset.StandardCharsets;

public class Test {
    public static void main(String[] args) {
        String text = "Hi \u00D0\u00A0!";
        System.out.println("Text length: " + text.length());
        for (char c : text.toCharArray()) {
            System.out.printf("'%c' (%d)\n", c, (int)c);
        }

        String decoded = decodeMixedUTF8WithColors(text);
        System.out.println("Decoded: " + decoded);
        for (char c : decoded.toCharArray()) {
            System.out.printf("'%c' (%d)\n", c, (int)c);
        }
    }

    public static String decodeMixedUTF8WithColors(String text) {
        int len = text.length();
        StringBuilder sbText = new StringBuilder(len);

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            if (c >= 0xC0 && c <= 0xF7 && i + 1 < len) {
                int b1 = c;
                int sequenceLen = 0;
                if ((b1 & 0xE0) == 0xC0) sequenceLen = 2;
                else if ((b1 & 0xF0) == 0xE0) sequenceLen = 3;
                else if ((b1 & 0xF8) == 0xF0) sequenceLen = 4;

                System.out.println("Checking sequence at " + i + ", len=" + sequenceLen);

                if (sequenceLen > 0 && i + sequenceLen <= len) {
                    boolean valid = true;
                    byte[] bytes = new byte[sequenceLen];
                    bytes[0] = (byte) b1;

                    for (int j = 1; j < sequenceLen; j++) {
                        char next = text.charAt(i + j);
                        System.out.println("  Next: " + (int)next + ", masked: " + (next & 0xC0));
                        if ((next & 0xC0) != 0x80) {
                            valid = false;
                            break;
                        }
                        bytes[j] = (byte) next;
                    }

                    if (valid) {
                        try {
                            sbText.append(new String(bytes, StandardCharsets.UTF_8));
                            i += sequenceLen - 1;
                            continue;
                        } catch (Exception ignored) {
                            ignored.printStackTrace();
                        }
                    } else {
                        System.out.println("  Invalid sequence");
                    }
                }
            }
            sbText.append(c);
        }
        return sbText.toString();
    }
}
