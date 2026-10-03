package com.college.student_service_platform.agent;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Assistant protocol message, independent of any network transport. */
public record DeepSeekReply(ObjectNode message, String finishReason) {
    public DeepSeekReply { message = message.deepCopy(); }
    @Override public ObjectNode message() { return message.deepCopy(); }
}
