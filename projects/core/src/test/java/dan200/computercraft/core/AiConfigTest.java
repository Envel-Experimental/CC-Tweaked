// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core;

import dan200.computercraft.test.core.ReplaceUnderscoresDisplayNameGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(ReplaceUnderscoresDisplayNameGenerator.class)
class AiConfigTest {

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        AiConfig.enabled = true;
    }

    @AfterEach
    void tearDown() {
        // Reset to default valid state so it doesn't pollute other tests
        AiConfig.endpoint = "https://api.openai.com/v1";
        AiConfig.maxContextTokens = 8192;
        AiConfig.maxResponseTokens = 1000;
        AiConfig.maxMessageChars = 2000;
        AiConfig.requireHttps = true;
        AiConfig.enabled = false;
    }

    @Test
    void default_state_is_valid() {
        AiConfig.enabled = false;
        assertDoesNotThrow(AiConfig::validate, "Default AiConfig values should be valid");
    }

    @Test
    void validate_fails_if_endpoint_is_blank() {
        AiConfig.endpoint = "   ";
        assertThrows(IllegalStateException.class, AiConfig::validate);
    }

    @Test
    void validate_fails_if_https_required_but_http_provided() {
        AiConfig.requireHttps = true;
        AiConfig.endpoint = "http://api.openai.com/v1";
        assertThrows(IllegalStateException.class, AiConfig::validate);
    }

    @Test
    void validate_passes_if_https_required_and_provided() {
        AiConfig.requireHttps = true;
        AiConfig.endpoint = "https://api.openai.com/v1";
        assertDoesNotThrow(AiConfig::validate);
    }

    @Test
    void validate_fails_if_tokens_negative() {
        AiConfig.maxContextTokens = -10;
        assertThrows(IllegalStateException.class, AiConfig::validate);
    }

    @Test
    void validate_fails_if_message_chars_negative() {
        AiConfig.maxMessageChars = -5;
        assertThrows(IllegalStateException.class, AiConfig::validate);
    }
}
