// SPDX-FileCopyrightText: 2023 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core;

import dan200.computercraft.core.computer.ComputerBootstrap;
import org.junit.jupiter.api.Test;

public class RussianLanguageIT {
    @Test
    public void testRussianCharacters() {
        ComputerBootstrap.run("""
            local testString = "Привет, мир!"
            local file = fs.open("test.txt", "w")
            file.write(testString)
            file.close()

            file = fs.open("test.txt", "r")
            local contents = file.readAll()
            file.close()

            assertion.assert(contents == testString, "File contents do not match. Got '" .. contents .. "', expected '" .. testString .. "'")
            """, 5);
    }
}
