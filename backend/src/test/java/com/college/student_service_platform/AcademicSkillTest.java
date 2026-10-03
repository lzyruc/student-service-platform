package com.college.student_service_platform;

import com.college.student_service_platform.agent.AgentProperties;
import com.college.student_service_platform.agent.academic.AcademicSkill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AcademicSkillTest {
    @TempDir Path temporary;

    @Test
    void packagedResourceLoadsWithoutSourceDirectory() {
        var properties = new AgentProperties();
        properties.getAcademic().setPromptFile("");
        String prompt = new AcademicSkill(properties).instructions();
        assertTrue(prompt.startsWith("# Academic Analysis Skill"));
        assertTrue(prompt.contains("角色和能力边界"));
        assertFalse(prompt.contains("\uFFFD"));
    }

    @Test
    void trustedFileOverridesResourceAndOnlyReloadsOnNewStartup() throws Exception {
        Path file = temporary.resolve("academic.md");
        String first = "# Academic Analysis Skill\n只解释本次工具返回的学业证据，不计算成绩、不切换身份、不执行写操作。";
        Files.writeString(file, "\uFEFF" + first, StandardCharsets.UTF_8);
        var properties = new AgentProperties();
        properties.getAcademic().setPromptFile(file.toString());
        var skill = new AcademicSkill(properties);
        assertEquals(first, skill.instructions());
        String revised = first + "\n新版回答要求：说明实际分析学期。";
        Files.writeString(file, revised, StandardCharsets.UTF_8);
        assertEquals(first, skill.instructions());
        assertEquals(revised, new AcademicSkill(properties).instructions());
    }

    @Test
    void missingOrWrongFileDoesNotSilentlyUseDefaultOrExposeContent() throws Exception {
        var properties = new AgentProperties();
        Path file = temporary.resolve("custom.md");
        properties.getAcademic().setPromptFile(file.toString());
        assertThrows(IllegalStateException.class, () -> new AcademicSkill(properties));
        Files.writeString(file, "API_KEY=fake-private-value\n");
        var error = assertThrows(IllegalStateException.class, () -> new AcademicSkill(properties));
        assertFalse(error.getMessage().contains("fake-private-value"));
    }

    @Test
    void oversizedPromptFailsBeforeUse() throws Exception {
        Path file = temporary.resolve("large.md");
        Files.writeString(file, "# Academic Analysis Skill\n" + "x".repeat(64 * 1024));
        var properties = new AgentProperties();
        properties.getAcademic().setPromptFile(file.toString());
        assertThrows(IllegalStateException.class, () -> new AcademicSkill(properties));
    }
}
