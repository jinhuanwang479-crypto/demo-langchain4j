package com.example.consultant.service;

import com.example.consultant.mapper.AiConversationMapper;
import com.example.consultant.mapper.AiMessageMapper;
import com.example.consultant.pojo.AiConversation;
import com.example.consultant.pojo.AiMessage;
import com.example.consultant.utils.UserContextUtil;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ConversationService {

    @Autowired
    private AiConversationMapper conversationMapper;

    @Autowired
    private AiMessageMapper messageMapper;

    @Autowired
    private ChatMemoryStore chatMemoryStore;

    @Transactional
    public void saveConversation(String memoryId, String userMessage, String assistantResponse, List<Map<String, Object>> allMessages) {
        // 会话数据按“租户:用户”做逻辑隔离，避免不同租户下相同 userId 串数据。
        String currentUserId = UserContextUtil.getTenantScopedUserId();

        AiConversation conversation = conversationMapper.findByMemoryIdAndUserId(memoryId, currentUserId);

        if (conversation != null) {
            if (allMessages != null && !allMessages.isEmpty()) {
                messageMapper.deleteByMemoryId(memoryId);

                List<AiMessage> messagesToSave = new ArrayList<>();
                for (Map<String, Object> msg : allMessages) {
                    AiMessage aiMsg = new AiMessage();
                    aiMsg.setMemoryId(memoryId);
                    aiMsg.setRole((String) msg.get("role"));
                    aiMsg.setContent((String) msg.get("content"));
                    messagesToSave.add(aiMsg);
                }
                messageMapper.batchInsert(messagesToSave);

                conversation.setMessageCount(messagesToSave.size());
            } else {
                AiMessage userMsg = new AiMessage();
                userMsg.setMemoryId(memoryId);
                userMsg.setRole("user");
                userMsg.setContent(userMessage);
                messageMapper.insert(userMsg);

                AiMessage assistantMsg = new AiMessage();
                assistantMsg.setMemoryId(memoryId);
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(assistantResponse);
                messageMapper.insert(assistantMsg);

                conversation.setMessageCount(conversation.getMessageCount() + 2);
            }

            conversationMapper.update(conversation);
        } else {
            conversation = new AiConversation();
            conversation.setMemoryId(memoryId);
            conversation.setUserId(currentUserId);
            conversation.setTitle(generateTitle(userMessage));
            conversation.setMessageCount(2);

            conversationMapper.insert(conversation);

            if (allMessages != null && !allMessages.isEmpty()) {
                List<AiMessage> messagesToSave = new ArrayList<>();
                for (Map<String, Object> msg : allMessages) {
                    AiMessage aiMsg = new AiMessage();
                    aiMsg.setMemoryId(memoryId);
                    aiMsg.setRole((String) msg.get("role"));
                    aiMsg.setContent((String) msg.get("content"));
                    messagesToSave.add(aiMsg);
                }
                messageMapper.batchInsert(messagesToSave);
                conversation.setMessageCount(messagesToSave.size());
            } else {
                AiMessage userMsg = new AiMessage();
                userMsg.setMemoryId(memoryId);
                userMsg.setRole("user");
                userMsg.setContent(userMessage);
                messageMapper.insert(userMsg);

                AiMessage assistantMsg = new AiMessage();
                assistantMsg.setMemoryId(memoryId);
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(assistantResponse);
                messageMapper.insert(assistantMsg);
            }

            conversationMapper.update(conversation);
        }
    }

    public List<Map<String, Object>> getAllConversations() {
        String currentUserId = UserContextUtil.getTenantScopedUserId();
        if (currentUserId == null || currentUserId.isEmpty()) {
            return new ArrayList<>();
        }

        // 只查询当前用户的会话列表
        List<AiConversation> conversations = conversationMapper.findAllByUserId(currentUserId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (AiConversation conv : conversations) {
            Map<String, Object> convMap = new HashMap<>();
            convMap.put("memoryId", conv.getMemoryId());
            convMap.put("title", conv.getTitle() != null ? conv.getTitle() : "新对话");
            convMap.put("messageCount", conv.getMessageCount());
            result.add(convMap);
        }

        return result;
    }

    public List<Map<String, Object>> getConversationDetail(String memoryId) {
        String currentUserId = UserContextUtil.getTenantScopedUserId();

        // 只查询属于当前用户的会话详情
        AiConversation conversation = conversationMapper.findByMemoryIdAndUserId(memoryId, currentUserId);

        if (conversation == null) {
            throw new RuntimeException("会话不存在或无权访问");
        }

        List<AiMessage> messages = messageMapper.findByMemoryId(memoryId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (AiMessage msg : messages) {
            Map<String, Object> msgMap = new HashMap<>();
            msgMap.put("role", msg.getRole());
            msgMap.put("content", msg.getContent());
            msgMap.put("timestamp", msg.getCreateTime().getTime());
            result.add(msgMap);
        }

        return result;
    }

    @Transactional
    public boolean deleteConversation(String memoryId) {
        try {
            String currentUserId = UserContextUtil.getTenantScopedUserId();

            // 先验证该会话是否属于当前用户
            AiConversation conversation = conversationMapper.findByMemoryIdAndUserId(memoryId, currentUserId);
            if (conversation == null) {
                return false; // 不属于当前用户，删除失败
            }

            messageMapper.deleteByMemoryId(memoryId);
            int deleted = conversationMapper.deleteByMemoryIdAndUserId(memoryId, currentUserId);
            chatMemoryStore.deleteMessages(memoryId);
            return deleted > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public void clearAllConversations() {
        String currentUserId = UserContextUtil.getTenantScopedUserId();
        List<AiConversation> conversations = conversationMapper.findAllByUserId(currentUserId);
        for (AiConversation conversation : conversations) {
            chatMemoryStore.deleteMessages(conversation.getMemoryId());
        }

        // 只清空当前用户的所有会话和消息
        messageMapper.deleteByUserId(currentUserId);
        conversationMapper.deleteAllByUserId(currentUserId);
    }

    private String generateTitle(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "新对话";
        }
        return content.length() > 20 ? content.substring(0, 20) + "..." : content;
    }
}
