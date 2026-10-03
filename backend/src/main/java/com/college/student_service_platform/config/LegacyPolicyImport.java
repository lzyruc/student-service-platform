package com.college.student_service_platform.config;

import com.college.student_service_platform.dto.PolicyDocumentSaveRequest;
import com.college.student_service_platform.repository.PolicyDocumentRepository;
import com.college.student_service_platform.repository.PolicyDocumentRepository.PolicyDocumentRow;
import com.college.student_service_platform.service.FileService;
import com.college.student_service_platform.service.PolicyDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** 一次性把旧本地文件登记为管理端政策；问答和日常入库不再扫描这个目录。 */
@Component
public class LegacyPolicyImport {
    private static final Logger log = LoggerFactory.getLogger(LegacyPolicyImport.class);
    private final PolicyDocumentRepository repository;
    private final PolicyDocumentService policyService;
    private final FileService fileService;
    private final Path sourceDirectory;

    public LegacyPolicyImport(PolicyDocumentRepository repository, PolicyDocumentService policyService,
                              FileService fileService,
                              @Value("${knowledge.legacy-policy-dir:}") String sourceDirectory) {
        this.repository = repository;
        this.policyService = policyService;
        this.fileService = fileService;
        this.sourceDirectory = (sourceDirectory == null || sourceDirectory.isBlank()
                ? Path.of("..", "python-services", "政策文件库") : Path.of(sourceDirectory))
                .toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        if (!Files.isDirectory(sourceDirectory)) return;
        String admin = repository.findFirstAdminUsername().orElse(null);
        if (admin == null) {
            log.warn("Legacy policy migration skipped: create an active admin account first");
            return;
        }

        boolean allRegistered = true;
        try (var paths = Files.list(sourceDirectory)) {
            List<Path> files = paths.filter(Files::isRegularFile).sorted().toList();
            for (Path source : files) {
                if (!isSupported(source)) {
                    allRegistered = false;
                    log.warn("Unsupported legacy policy file retained: {}", source.getFileName());
                    continue;
                }
                Long policyId;
                try {
                    PolicyDocumentRow existing = findIdenticalPolicy(source);
                    if (existing != null) {
                        policyId = existing.document().getId();
                        if ("READY".equals(existing.document().getIngestStatus())
                                || "PROCESSING".equals(existing.document().getIngestStatus())) continue;
                    } else {
                        Long adminId = repository.findAdminIdByUsername(admin).orElseThrow();
                        Long fileId = fileService.importPolicyFile(source, adminId).getId();
                        policyId = policyService.create(requestFor(source, fileId), admin).id();
                        log.info("Registered legacy policy {} as {}", source.getFileName(), policyId);
                    }
                } catch (Exception exception) {
                    allRegistered = false;
                    log.error("Could not register legacy policy {}; source retained", source.getFileName(), exception);
                    continue;
                }
                try {
                    policyService.publish(policyId);
                } catch (Exception exception) {
                    // 原文件已经由 uploads 保存，失败记录可直接在 Web 管理端重新发布。
                    log.error("Legacy policy {} registered; retry publication in Web admin", policyId, exception);
                }
            }
        } catch (IOException exception) {
            log.error("Could not read legacy policy directory", exception);
            return;
        }

        if (allRegistered) {
            Path backup = sourceDirectory.resolveSibling(sourceDirectory.getFileName() + ".imported");
            try {
                // 不覆盖既有备份，也不删除任何原始文件。
                Files.move(sourceDirectory, backup);
                log.info("Legacy policy migration finished; original files archived at {}", backup);
            } catch (IOException exception) {
                log.warn("Legacy policies registered; could not archive source directory", exception);
            }
        }
    }

    private PolicyDocumentRow findIdenticalPolicy(Path source) throws IOException {
        for (PolicyDocumentRow row : repository.findByOriginalName(source.getFileName().toString())) {
            Path stored = fileService.getFilePath(fileService.getFileById(row.document().getFileId()));
            if (Files.isRegularFile(stored) && Files.mismatch(source, stored) == -1) return row;
        }
        return null;
    }

    private boolean isSupported(Path source) {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private PolicyDocumentSaveRequest requestFor(Path source, Long fileId) {
        String name = source.getFileName().toString();
        boolean image = !name.toLowerCase(Locale.ROOT).endsWith(".pdf");
        PolicyDocumentSaveRequest request = new PolicyDocumentSaveRequest();
        request.setTitle(image ? "校历" : name.replaceFirst("(?i)\\.pdf$", ""));
        request.setCategory(image ? "校历" : name.contains("违纪") || name.contains("处分") ? "违纪处分" : "学籍管理");
        request.setAudience(name.contains("本科") ? "UNDERGRADUATE" : name.contains("研究生") ? "POSTGRADUATE" : "ALL");
        request.setVersion("v1.0");
        request.setTags(List.of("历史政策迁入"));
        request.setRemark("由旧本地政策文件库一次性迁入；原文件由后端统一保管");
        request.setFileId(fileId);
        return request;
    }
}
