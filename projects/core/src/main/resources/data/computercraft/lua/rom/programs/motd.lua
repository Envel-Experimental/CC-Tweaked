-- SPDX-FileCopyrightText: 2020 The CC: Tweaked Developers
--
-- SPDX-License-Identifier: MPL-2.0

local date = os.date("*t")
if date.month == 1 and date.day == 1 then
    print("Happy new year!")
elseif date.month == 12 and date.day == 24 then
    print("Merry X-mas!")
elseif date.month == 10 and date.day == 31 then
    print("OOoooOOOoooo! Spooky!")
elseif date.month == 4 and date.day == 28 then
    print("Ed Balls")
else
    local tMotd = {}

    local function utf8_to_terminal(str)
        local res = {}
        for p, cp in utf8.codes(str) do
            if cp >= 0x0410 and cp <= 0x044F then
                table.insert(res, string.char(cp - 0x0410 + 192))
            elseif cp == 0x0401 then
                table.insert(res, string.char(168))
            elseif cp == 0x0451 then
                table.insert(res, string.char(184))
            elseif cp < 128 or (cp >= 160 and cp <= 255) then
                table.insert(res, string.char(cp))
            else
                table.insert(res, "?")
            end
        end
        return table.concat(res)
    end

    for sPath in string.gmatch(settings.get("motd.path"), "[^:]+") do
        if fs.exists(sPath) then
            for sLine in io.lines(sPath) do
                table.insert(tMotd, utf8_to_terminal(sLine))
            end
        end
    end

    if #tMotd == 0 then
        print("missingno")
    else
        print(tMotd[math.random(1, #tMotd)])
    end
end
