package com.conceptualware.api.websocket;

import com.conceptualware.core.algorithms.sorting.SortStep;
import com.conceptualware.core.algorithms.sorting.SortingAlgorithms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

@Controller
public class AlgorithmWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmWebSocketHandler.class);

    private static final long MAX_DELAY_MS = 1_000L;
    private static final int MAX_INPUT_SIZE = SortingAlgorithms.MAX_STEP_VISUALIZATION_SIZE;
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final SimpMessagingTemplate messagingTemplate;
    private final Executor virtualThreadExecutor;

    public AlgorithmWebSocketHandler(SimpMessagingTemplate messagingTemplate,
                                     @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
        this.messagingTemplate = messagingTemplate;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @MessageMapping("/execute-steps")
    public void executeWithSteps(@Payload ExecutionRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || !SAFE_SESSION_ID.matcher(sessionId).matches()) {
            log.warn("Rejeitada execução por sessionId inválido");
            return;
        }

        String destination = "/topic/algorithm-steps/" + sessionId;

        if (request.input() == null || request.input().isEmpty()) {
            sendError(destination, "Entrada vazia");
            return;
        }
        if (request.input().size() > MAX_INPUT_SIZE) {
            sendError(destination, "Entrada limitada a " + MAX_INPUT_SIZE + " elementos");
            return;
        }
        if (request.input().stream().anyMatch(java.util.Objects::isNull)) {
            sendError(destination, "Entrada contém valores nulos");
            return;
        }

        long delayMs = Math.clamp(request.delayMs(), 0L, MAX_DELAY_MS);

        virtualThreadExecutor.execute(() -> streamSteps(destination, request, delayMs));
    }

    private void streamSteps(String destination, ExecutionRequest request, long delayMs) {
        try {
            int[] arr = request.input().stream().mapToInt(Integer::intValue).toArray();
            List<SortStep> steps = collectSteps(request.algorithmSlug(), arr);

            messagingTemplate.convertAndSend(destination,
                Map.of("type", "START", "totalSteps", steps.size()));

            for (int i = 0; i < steps.size(); i++) {
                SortStep frame = steps.get(i);
                messagingTemplate.convertAndSend(destination, Map.of(
                    "type",       "STEP",
                    "stepIndex",  i,
                    "comparing",  frame.comparing(),
                    "swapping",   frame.swapping(),
                    "array",      frame.array()
                ));
                Thread.sleep(delayMs);
            }

            messagingTemplate.convertAndSend(destination,
                Map.of("type", "DONE", "finalArray", arr));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendError(destination, "Execução interrompida");
        } catch (IllegalArgumentException e) {
            sendError(destination, e.getMessage());
        } catch (Exception e) {
            log.error("WebSocket execution error", e);
            sendError(destination, "Falha ao executar o algoritmo");
        }
    }

    private void sendError(String destination, String message) {
        messagingTemplate.convertAndSend(destination, Map.of("type", "ERROR", "message", message));
    }

    private List<SortStep> collectSteps(String slug, int[] arr) {
        return switch (slug == null ? "" : slug) {
            case "bubble-sort" -> SortingAlgorithms.bubbleSortWithSteps(arr);
            case "insertion-sort" -> SortingAlgorithms.insertionSortWithSteps(arr);
            default -> List.of();
        };
    }

    public record ExecutionRequest(
        String sessionId,
        String algorithmSlug,
        List<Integer> input,
        long delayMs
    ) {}
}
