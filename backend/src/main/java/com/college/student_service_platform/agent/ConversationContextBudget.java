package com.college.student_service_platform.agent;

import com.college.student_service_platform.entity.AgentConversationMessage;
import java.util.ArrayList;
import java.util.List;

/** One source of limits for HTTP input, DB history selection, and model-facing validation. */
public final class ConversationContextBudget {
    public static final int MAX_USER_CHARS = 2000;
    public static final int MAX_MESSAGES = 20;
    public static final int MAX_CONTENT_CHARS = 4000;
    public static final int MAX_CONTEXT_CHARS = 20000;
    public static final int MAX_SCANNED_MESSAGES = 40;
    private ConversationContextBudget() { }

    public static List<AgentChatRequest.Turn> recentCompletePairs(List<AgentConversationMessage> rows, String question) {
        var result = new ArrayList<AgentChatRequest.Turn>(); int length = question.length();
        // Start with newest complete pairs. Drop oldest first when the shared budget is exhausted.
        for (int i = rows.size() - 1; i > 0; i--) {
            var assistant = rows.get(i); var user = rows.get(i - 1);
            if (!"ASSISTANT".equals(assistant.role()) || !"USER".equals(user.role())) continue;
            i--;
            if (!validContent(user.content()) || !validContent(assistant.content())) continue;
            int pairLength = user.content().length() + assistant.content().length();
            if (result.size() + 2 > MAX_MESSAGES || length + pairLength > MAX_CONTEXT_CHARS) break;
            result.add(0, new AgentChatRequest.Turn("assistant", assistant.content()));
            result.add(0, new AgentChatRequest.Turn("user", user.content())); length += pairLength;
        }
        return List.copyOf(result);
    }
    private static boolean validContent(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_CONTENT_CHARS;
    }
}
