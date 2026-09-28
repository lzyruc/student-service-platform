package com.college.student_service_platform.config;

import com.college.student_service_platform.repository.PolicyDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PolicyIngestionRecovery implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PolicyIngestionRecovery.class);

    private final PolicyDocumentRepository repository;

    public PolicyIngestionRecovery(PolicyDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int recovered = repository.failInterruptedIngestions();
        if (recovered > 0) {
            log.warn("Marked {} interrupted policy ingestion task(s) as FAILED", recovered);
        }
    }
}
