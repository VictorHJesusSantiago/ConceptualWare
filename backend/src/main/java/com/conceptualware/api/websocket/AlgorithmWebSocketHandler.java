package com.conceptualware.api.websocket;

import com.conceptualware.core.algorithms.sorting.SortingAlgorithms;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Concept #18 — Async: WebSocket STOMP, reactive step streaming
 * Concept #17 — Concurrency: Virtual Thread per execution, non-blocking publisher
 * Concept #6  — Event-driven architecture: pub/sub via STOMP broker
 *
 * Clients subscribe to /topic/algorithm-steps/{sessionId}
 * then send to /app/execute-steps to trigger streaming execution.
 */
@Controller
public class AlgorithmWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmWebSocketHandler.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public AlgorithmWebSocketHandler(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives an execution request and streams algorithm steps to the client.
     * Each step carries: stepIndex, comparing indices, swapping indices, array state.
     */
    @MessageMapping("/execute-steps")
    public void executeWithSteps(@Payload ExecutionRequest request) {
        String destination = "/topic/algorithm-steps/" + request.sessionId();

        // Run on a virtual thread so the STOMP thread is not blocked (Concept #17)
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                int[] arr = request.input().stream().mapToInt(Integer::intValue).toArray();
                List<StepFrame> steps = collectSteps(request.algorithmSlug(), arr);

                messagingTemplate.convertAndSend(destination,
                    Map.of("type", "START", "totalSteps", steps.size()));

                for (int i = 0; i < steps.size(); i++) {
                    StepFrame frame = steps.get(i);
                    messagingTemplate.convertAndSend(destination, Map.of(
                        "type",       "STEP",
                        "stepIndex",  i,
                        "comparing",  frame.comparing(),
                        "swapping",   frame.swapping(),
                        "array",      frame.array()
                    ));
                    // Simulate step delay for visualization (Concept #18 — throttling)
                    Thread.sleep(request.delayMs());
                }

                messagingTemplate.convertAndSend(destination,
                    Map.of("type", "DONE", "finalArray", arr));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                messagingTemplate.convertAndSend(destination, Map.of("type", "ERROR", "message", "Interrupted"));
            } catch (Exception e) {
                log.error("WebSocket execution error", e);
                messagingTemplate.convertAndSend(destination, Map.of("type", "ERROR", "message", e.getMessage()));
            }
        });
    }

    private List<StepFrame> collectSteps(String slug, int[] arr) {
        return switch (slug) {
            case "bubble-sort" -> SortingAlgorithms.bubbleSortWithSteps(arr);
            case "insertion-sort" -> SortingAlgorithms.insertionSortWithSteps(arr);
            default -> List.of(); // Other algorithms return final result directly
        };
    }

    // ── Value records ─────────────────────────────────────────────────────────

    public record ExecutionRequest(
        String sessionId,
        String algorithmSlug,
        List<Integer> input,
        long delayMs
    ) {}

    public record StepFrame(
        int[] comparing,
        int[] swapping,
        int[] array
    ) {}
}
