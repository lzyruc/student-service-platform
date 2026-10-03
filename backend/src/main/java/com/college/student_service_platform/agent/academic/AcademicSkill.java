package com.college.student_service_platform.agent.academic;

import com.college.student_service_platform.agent.AgentProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Trusted startup configuration, never user-provided conversation content. */
@Component
public final class AcademicSkill {
    public static final String DEFAULT_PROMPT_FILE = "./src/main/resources/agent/skills/academic.md";
    private static final String RESOURCE = "/agent/skills/academic.md";
    private static final int MAX_BYTES = 64 * 1024;
    private final String instructions;

    public AcademicSkill(AgentProperties properties) {
        instructions = load(properties.getAcademic().getPromptFile());
    }

    public String instructions() { return instructions; }

    private static String load(String configured) {
        String location = configured == null ? "" : configured.trim();
        try {
            byte[] bytes;
            if (!location.isEmpty() && Files.exists(Path.of(location))) {
                Path path = Path.of(location);
                if (Files.size(path) > MAX_BYTES) throw invalid();
                bytes = Files.readAllBytes(path);
            } else {
                // A packaged deployment need not retain the source directory.
                if (!location.isEmpty() && !DEFAULT_PROMPT_FILE.equals(location)) throw invalid();
                try (var stream = AcademicSkill.class.getResourceAsStream(RESOURCE)) {
                    if (stream == null) throw invalid();
                    bytes = stream.readNBytes(MAX_BYTES + 1);
                }
            }
            if (bytes.length > MAX_BYTES) throw invalid();
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            text = text.strip();
            if (!text.startsWith("# Academic Analysis Skill") || text.length() < 40) throw invalid();
            return text;
        } catch (IOException | InvalidPathException e) {
            throw invalid();
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Academic Skill prompt 无法加载：请检查可信 UTF-8 文件、标题及大小，修改后重启后端");
    }
}
