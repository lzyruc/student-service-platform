package com.college.student_service_platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Assistant protocol plus optional provider-reported usage; no estimated tokens. */
public record DeepSeekReply(ObjectNode message, String finishReason, TokenUsage usage, String model) {
    public DeepSeekReply(ObjectNode message, String finishReason) { this(message, finishReason, null, null); }
    public DeepSeekReply { message = message.deepCopy(); }
    @Override public ObjectNode message() { return message.deepCopy(); }
    public record TokenUsage(Long promptTokens, Long completionTokens, Long totalTokens,
                             Long promptCacheHitTokens, Long promptCacheMissTokens) {
        static TokenUsage from(JsonNode node) {
            if (!node.isObject()) return null;
            return new TokenUsage(value(node,"prompt_tokens"),value(node,"completion_tokens"),value(node,"total_tokens"),
                    value(node,"prompt_cache_hit_tokens"),value(node,"prompt_cache_miss_tokens"));
        }
        private static Long value(JsonNode node,String key) {
            var value=node.path(key);
            return value.isIntegralNumber() && value.canConvertToLong() && value.longValue()>=0 ? value.longValue() : null;
        }
    }
}
