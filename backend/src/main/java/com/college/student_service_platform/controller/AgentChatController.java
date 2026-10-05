package com.college.student_service_platform.controller;

import com.college.student_service_platform.agent.ConversationChatRequest;
import com.college.student_service_platform.agent.AgentExecutionEvent;
import com.college.student_service_platform.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.OutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import com.college.student_service_platform.dto.AgentConversationDtos.ChatResult;
import com.college.student_service_platform.service.ConversationChatService;
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
    private final ConversationChatService chats;
    public AgentChatController(ConversationChatService chats) { this.chats = chats; }

    @PostMapping("/chat")
    public Result<ChatResult> chat(@RequestBody JsonNode body, HttpServletRequest request) {
        return Result.success(chats.chat(chats.prepare(ConversationChatRequest.parse(body), request), event -> { }));
    }

    @PostMapping(value = "/chat/stream", produces = "application/x-ndjson;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> stream(@RequestBody JsonNode body, HttpServletRequest request) {
        var prepared = chats.prepare(ConversationChatRequest.parse(body), request);
        StreamingResponseBody stream = output -> {
            try {
                var result = chats.chat(prepared, event -> send(output, event));
                send(output, new AgentExecutionEvent("result", result));
            } catch (UncheckedIOException disconnected) {
                throw disconnected.getCause();
            } catch (ApiException failure) {
                send(output, new AgentExecutionEvent("error", Map.of("code", failure.getStatus().value(), "message", failure.getMessage())));
            } catch (RuntimeException failure) {
                send(output, new AgentExecutionEvent("error", Map.of("code", 500, "message", "学业分析暂时无法完成，请稍后重试")));
            }
        };
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Accel-Buffering", "no")
                .header("Content-Type", "application/x-ndjson;charset=UTF-8").body(stream);
    }

    private static final ObjectMapper STREAM_MAPPER = new ObjectMapper().findAndRegisterModules();
    private void send(OutputStream output, AgentExecutionEvent event) {
        try {
            output.write(STREAM_MAPPER.writeValueAsBytes(event)); output.write('\n'); output.flush();
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> malformedBody() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(400, "聊天请求必须为合法 JSON"));
    }
}
