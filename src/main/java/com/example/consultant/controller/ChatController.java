package com.example.consultant.controller;

import com.example.consultant.service.AiChatObservationService;
import com.example.consultant.service.ConversationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping
/**
 * AI 对话主入口控制器。
 * 当前控制器本身尽量保持轻量：
 * 1. `/chat` 交给 AiChatObservationService 承担流式对话与监测编排；
 * 2. 历史会话接口仍交给 ConversationService 处理。
 */
public class ChatController {

    private final AiChatObservationService aiChatObservationService;
    private final ConversationService conversationService;

    public ChatController(AiChatObservationService aiChatObservationService,
                          ConversationService conversationService) {
        this.aiChatObservationService = aiChatObservationService;
        this.conversationService = conversationService;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    /**
     * 流式聊天接口。
     * 返回 SSE 文本流，前端可以边接收边渲染。
     */
    public Flux<String> chat(@RequestParam String memoryId, @RequestParam String message) {
        return Flux.create(emitter -> aiChatObservationService.streamChat(memoryId, message, emitter),
                FluxSink.OverflowStrategy.BUFFER);
    }

    @GetMapping("/conversations")
    /** 查询当前用户的历史会话列表。 */
    public Map<String, Object> getConversations() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> conversations = conversationService.getAllConversations();
            result.put("code", 200);
            result.put("result", conversations);
            result.put("success", true);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "获取失败: " + e.getMessage());
            result.put("success", false);
        }
        return result;
    }

    @GetMapping("/conversation/{memoryId}")
    /** 查询某个会话下的消息明细。 */
    public Map<String, Object> getConversationDetail(@PathVariable String memoryId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> messages = conversationService.getConversationDetail(memoryId);
            result.put("code", 200);
            result.put("result", messages);
            result.put("success", true);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "获取失败: " + e.getMessage());
            result.put("success", false);
        }
        return result;
    }

    @PostMapping("/conversation/save")
    /** 手动保存一轮或整段会话到业务历史表。 */
    public Map<String, Object> saveConversation(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String memoryId = (String) request.get("memoryId");
            String message = (String) request.get("message");
            String response = (String) request.get("response");
            List<Map<String, Object>> allMessages = (List<Map<String, Object>>) request.get("allMessages");
            conversationService.saveConversation(memoryId, message, response, allMessages);

            result.put("success", true);
            result.put("memoryId", memoryId);
            result.put("code", 200);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "保存失败: " + e.getMessage());
            result.put("success", false);
        }
        return result;
    }

    @DeleteMapping("/conversation/{memoryId}")
    /** 删除指定会话。 */
    public Map<String, Object> deleteConversation(@PathVariable String memoryId) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = conversationService.deleteConversation(memoryId);
            result.put("success", success);
            result.put("message", success ? "删除成功" : "删除失败");
            result.put("code", success ? 200 : 500);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "删除失败: " + e.getMessage());
            result.put("success", false);
        }
        return result;
    }

    @DeleteMapping("/conversations/clear")
    /** 清空当前用户的全部历史会话。 */
    public Map<String, Object> clearAllConversations() {
        Map<String, Object> result = new HashMap<>();
        try {
            conversationService.clearAllConversations();
            result.put("success", true);
            result.put("message", "已清空所有历史");
            result.put("code", 200);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "清空失败: " + e.getMessage());
            result.put("success", false);
        }
        return result;
    }
}
