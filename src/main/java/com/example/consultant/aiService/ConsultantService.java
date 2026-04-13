package com.example.consultant.aiService;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
//        chatMemory = "chatMemory"//配置聊天记忆的名称,
        chatMemoryProvider = "chatMemoryProvider",//配置聊天记忆提供者的名称
        contentRetriever = "contentRetriever",//配置向量数据库的检索对象
        tools = {
                "systemManagementTool",
                "materialTool",
                "inventoryBillTool",
                "financeTool",
                "reportTool"
        }
)
public interface ConsultantService {
    @SystemMessage(fromResource = "system.txt")
    TokenStream chat(@MemoryId String memoryId,
                     @V("availableToolGuidance") String availableToolGuidance,
                     @UserMessage String input);
}
