package com.codesense.codesense.controller;

import com.codesense.codesense.model.ChatMessage;
import com.codesense.codesense.service.MultiTurnChatService;
import com.codesense.codesense.service.RagChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService     ragChatService;
    private final MultiTurnChatService multiTurnChatService;

    /**
     * Single-turn RAG Q&A (cached).
     *
     * curl "http://localhost:8080/api/chat/ask?project=my-app&q=How+does+UserService+work"
     */
    @GetMapping("/ask")
    public ResponseEntity<ChatMessage> ask(
            @RequestParam("project") String projectName,
            @RequestParam("q") String question
    ) {
        ChatMessage response = ragChatService.ask(projectName, question);
        return ResponseEntity.ok(response);
    }

    /**
     * Single-turn streaming RAG — tokens arrive in real time.
     * Frontend: use EventSource or fetch with ReadableStream.
     *
     * curl -N "http://localhost:8080/api/chat/stream?project=my-app&q=Explain+PaymentService"
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam("project") String projectName,
            @RequestParam("q") String question
    ) {
        return ragChatService.stream(projectName, question);
    }

    /**
     * Multi-turn conversation — maintains context across messages.
     * Client must send the same sessionId across all messages in a conversation.
     *
     * curl -X POST http://localhost:8080/api/chat/conversation \
     *   -H "Content-Type: application/json" \
     *   -d '{"sessionId":"abc-123","project":"my-app","message":"How does auth work?"}'
     */
    @PostMapping(value = "/conversation", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> conversation(@RequestBody ConversationRequest req) {
        String sessionId = (req.sessionId() != null && !req.sessionId().isBlank())
                ? req.sessionId()
                : UUID.randomUUID().toString();  // auto-generate if not provided

        return multiTurnChatService.chat(sessionId, req.project(), req.message());
    }

    /**
     * Clear a conversation session (user clicks "New Chat").
     */
    @DeleteMapping("/conversation/{sessionId}")
    public ResponseEntity<Map<String, String>> clearSession(
            @PathVariable String sessionId
    ) {
        multiTurnChatService.clearSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "Session cleared: " + sessionId));
    }

    record ConversationRequest(String sessionId, String project, String message) {}
}