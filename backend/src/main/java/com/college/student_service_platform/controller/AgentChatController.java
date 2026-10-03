package com.college.student_service_platform.controller;

import com.college.student_service_platform.agent.AgentChatRequest;
import com.college.student_service_platform.agent.AgentChatResponse;
import com.college.student_service_platform.agent.SingleAgentService;
import com.college.student_service_platform.common.Result;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/agent")
public class AgentChatController {
    private final SingleAgentService agent;
    public AgentChatController(SingleAgentService agent) { this.agent = agent; }

    @PostMapping("/chat")
    public Result<AgentChatResponse> chat(@RequestBody JsonNode body, HttpServletRequest request) {
        return Result.success(agent.chat(AgentChatRequest.parse(body), request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> malformedBody() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(400, "聊天请求必须为合法 JSON"));
    }
}
