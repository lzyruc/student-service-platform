package com.college.student_service_platform;

import com.college.student_service_platform.agent.AgentProperties;
import com.college.student_service_platform.agent.DeepSeekSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekSettingsTest {
    @TempDir Path temporary;

    @Test
    void sharesDotEnvWithPolicyAndReadsQuotesBomAndCommentsWithoutImportingOtherSecrets() throws Exception {
        Path env = temporary.resolve(".env");
        Files.writeString(env, "\uFEFF# local settings\nexport DEEPSEEK_API_KEY='test-only-key' # note\n"
                + "DEEPSEEK_BASE_URL=\"https://api.deepseek.com/v1/\"\nDEEPSEEK_MODEL=deepseek-flash # comment\nJWT_SECRET=ignored\n");
        var properties = new AgentProperties();
        properties.getDeepseek().setEnvFile(env.toString());
        var settings = new DeepSeekSettings(properties.getDeepseek());
        assertEquals("test-only-key", settings.apiKey());
        assertEquals("https://api.deepseek.com/v1/chat/completions", settings.completionsUri().toString());
        assertEquals("deepseek-flash", settings.model());
        assertFalse(settings.toString().contains("test-only-key"));
    }

    @Test
    void explicitSettingsOverrideSharedFileAndMissingFileDoesNotBreakOtherModules() throws Exception {
        Path env = temporary.resolve(".env");
        Files.writeString(env, "DEEPSEEK_API_KEY=file-key\nDEEPSEEK_MODEL=file-model\n");
        var p = new AgentProperties().getDeepseek();
        p.setEnvFile(env.toString());
        p.setApiKey("environment-key");
        p.setModel("deepseek-flash");
        assertEquals("environment-key", new DeepSeekSettings(p).apiKey());
        assertEquals("deepseek-flash", new DeepSeekSettings(p).model());
        p.setEnvFile(temporary.resolve("absent.env").toString());
        p.setApiKey("");
        assertEquals("", new DeepSeekSettings(p).apiKey());
    }

    @Test
    void rejectsBadConfigurationWithoutEchoingSecretValues() throws Exception {
        Path env = temporary.resolve(".env");
        Files.writeString(env, "DEEPSEEK_API_KEY=\"secret-value\n");
        var p = new AgentProperties().getDeepseek();
        p.setEnvFile(env.toString());
        assertFalse(assertThrows(IllegalArgumentException.class, () -> new DeepSeekSettings(p)).getMessage().contains("secret-value"));
        Files.writeString(env, "DEEPSEEK_API_KEY=${OTHER_KEY}\n");
        assertThrows(IllegalArgumentException.class, () -> new DeepSeekSettings(p));
        p.setEnvFile(temporary.resolve("absent.env").toString());
        p.setBaseUrl("https://user:secret@api.deepseek.com");
        assertFalse(assertThrows(IllegalArgumentException.class, () -> new DeepSeekSettings(p)).getMessage().contains("secret"));
    }

    @Test
    void validatesLoopAndTimeoutBudgets() {
        var p = new AgentProperties();
        p.validate();
        p.setMaxToolRounds(5);
        assertThrows(IllegalArgumentException.class, p::validate);
        p.setMaxToolRounds(4);
        p.setMaxToolCalls(0);
        assertThrows(IllegalArgumentException.class, p::validate);
        p.setMaxToolCalls(12);
        p.setRequestTimeout(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, p::validate);
    }
}
