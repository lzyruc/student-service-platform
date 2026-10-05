package com.college.student_service_platform.controller;

import com.college.student_service_platform.agent.ConversationChatRequest;
import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.dto.AgentConversationDtos.*;
import com.college.student_service_platform.service.AgentConversationService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

@RestController
@RequestMapping("/api/student/agent/conversations")
public class AgentConversationController {
    private final AgentConversationService service;
    public AgentConversationController(AgentConversationService service) { this.service = service; }
    @PostMapping
    public Result<Summary> create(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        String title = null;
        if (body != null) {
            ConversationChatRequest.requireObject(body, Set.of("title"));
            if (body.has("title")) title = ConversationChatRequest.text(body, "title");
        }
        return Result.success(service.create(request, title));
    }
    @GetMapping
    public Result<Page> list(@RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        return Result.success(service.list(request, page, pageSize));
    }
    @GetMapping("/{id}")
    public Result<Detail> detail(@PathVariable long id, @RequestParam(required = false) Long beforeMessageId,
                               @RequestParam(defaultValue = "100") int limit, HttpServletRequest request) {
        return Result.success(service.detail(request, id, beforeMessageId, limit));
    }
    @PatchMapping("/{id}")
    public Result<Summary> rename(@PathVariable long id, @RequestBody JsonNode body, HttpServletRequest request) {
        ConversationChatRequest.requireObject(body, Set.of("title"));
        return Result.success(service.rename(request, id, ConversationChatRequest.text(body, "title")));
    }
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id, HttpServletRequest request) {
        service.delete(request, id); return Result.success(null);
    }
}
