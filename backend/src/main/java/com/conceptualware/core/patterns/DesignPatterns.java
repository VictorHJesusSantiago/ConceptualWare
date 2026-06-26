package com.conceptualware.core.patterns;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Concept #13 — Todos os 23 Padrões de Projeto GoF + extras:
 *
 * CRIACIONAIS: Singleton, Factory Method, Abstract Factory, Builder, Prototype, Object Pool
 * ESTRUTURAIS:  Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy
 * COMPORTAMENTAIS: Chain of Responsibility, Command, Iterator, Mediator, Memento,
 *                  Observer, State, Strategy, Template Method, Visitor, Interpreter,
 *                  Null Object
 *
 * Concept #7  — OOP: interfaces, abstract classes, generics, inner classes
 * Concept #14 — Princípios: SOLID, DRY, SoC
 * Concept #12 — Anti-padrões documentados
 */
public class DesignPatterns {

    // ══════════════════════════════════════════════════════════════════════════
    // CRIACIONAIS
    // ══════════════════════════════════════════════════════════════════════════

    // ── Singleton (thread-safe via enum) ─────────────────────────────────────

    public enum AlgorithmRegistry {
        INSTANCE;
        private final Map<String, Object> registry = new ConcurrentHashMap<>();
        public void register(String name, Object algo) { registry.put(name, algo); }
        public Object get(String name) { return registry.get(name); }
    }

    // ── Factory Method ────────────────────────────────────────────────────────

    public interface Sortable {
        int[] sort(int[] arr);
        String name();
    }

    public abstract static class SorterFactory {
        public abstract Sortable createSorter();

        public int[] sortArray(int[] arr) {
            return createSorter().sort(arr);
        }
    }

    public static class MergeSorterFactory extends SorterFactory {
        @Override
        public Sortable createSorter() {
            return new Sortable() {
                public int[] sort(int[] arr) {
                    return com.conceptualware.core.algorithms.sorting.SortingAlgorithms.mergeSort(arr);
                }
                public String name() { return "MergeSort"; }
            };
        }
    }

    public static class QuickSorterFactory extends SorterFactory {
        @Override
        public Sortable createSorter() {
            return new Sortable() {
                public int[] sort(int[] arr) {
                    return com.conceptualware.core.algorithms.sorting.SortingAlgorithms.quickSort(arr);
                }
                public String name() { return "QuickSort"; }
            };
        }
    }

    // ── Abstract Factory ──────────────────────────────────────────────────────

    public interface DataStructureFactory<T extends Comparable<T>> {
        com.conceptualware.core.datastructures.tree.BinarySearchTree<T> createTree();
        com.conceptualware.core.datastructures.linear.Stack<T> createStack();
    }

    public static class DefaultDataStructureFactory<T extends Comparable<T>>
            implements DataStructureFactory<T> {
        @Override
        public com.conceptualware.core.datastructures.tree.BinarySearchTree<T> createTree() {
            return new com.conceptualware.core.datastructures.tree.BinarySearchTree<>();
        }
        @Override
        public com.conceptualware.core.datastructures.linear.Stack<T> createStack() {
            return new com.conceptualware.core.datastructures.linear.Stack<>();
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public record AlgorithmConfig(String name, int timeoutMs, int maxInputSize,
                                   boolean memoEnabled, String complexity) {

        public static Builder builder(String name) { return new Builder(name); }

        public static final class Builder {
            private final String name;
            private int timeoutMs = 5000;
            private int maxInputSize = 10_000;
            private boolean memoEnabled = true;
            private String complexity = "Unknown";

            private Builder(String name) { this.name = name; }
            public Builder timeoutMs(int v)    { timeoutMs = v; return this; }
            public Builder maxInputSize(int v)  { maxInputSize = v; return this; }
            public Builder memoEnabled(boolean v){ memoEnabled = v; return this; }
            public Builder complexity(String v)  { complexity = v; return this; }
            public AlgorithmConfig build() {
                return new AlgorithmConfig(name, timeoutMs, maxInputSize, memoEnabled, complexity);
            }
        }
    }

    // ── Prototype ─────────────────────────────────────────────────────────────

    public static class AlgorithmSnapshot implements Cloneable {
        public String name;
        public List<Integer> steps;
        public long executionTimeNs;

        public AlgorithmSnapshot(String name) {
            this.name = name;
            this.steps = new ArrayList<>();
        }

        @Override
        public AlgorithmSnapshot clone() {
            try {
                AlgorithmSnapshot clone = (AlgorithmSnapshot) super.clone();
                clone.steps = new ArrayList<>(this.steps);
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ── Object Pool ───────────────────────────────────────────────────────────

    public static class ObjectPool<T> {
        private final Queue<T> pool = new ConcurrentLinkedQueue<>();
        private final Supplier<T> factory;
        private final int maxSize;
        private int created = 0;

        public ObjectPool(Supplier<T> factory, int maxSize) {
            this.factory = factory;
            this.maxSize = maxSize;
        }

        public T acquire() {
            T obj = pool.poll();
            if (obj == null) {
                if (created < maxSize) { created++; return factory.get(); }
                throw new RuntimeException("Pool exhausted");
            }
            return obj;
        }

        public void release(T obj) { pool.offer(obj); }
        public int available() { return pool.size(); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ESTRUTURAIS
    // ══════════════════════════════════════════════════════════════════════════

    // ── Adapter ───────────────────────────────────────────────────────────────

    public interface NumberSorter { List<Integer> sort(List<Integer> numbers); }

    public static class LegacySorter {
        public int[] sortArray(int[] arr) {
            return com.conceptualware.core.algorithms.sorting.SortingAlgorithms.mergeSort(arr);
        }
    }

    public static class LegacySorterAdapter implements NumberSorter {
        private final LegacySorter legacy = new LegacySorter();

        @Override
        public List<Integer> sort(List<Integer> numbers) {
            int[] arr = numbers.stream().mapToInt(i -> i).toArray();
            int[] sorted = legacy.sortArray(arr);
            List<Integer> result = new ArrayList<>();
            for (int n : sorted) result.add(n);
            return result;
        }
    }

    // ── Bridge ────────────────────────────────────────────────────────────────

    public interface Renderer { String render(String content, String format); }
    public static class JsonRenderer implements Renderer {
        public String render(String content, String format) {
            return """
                {"content": "%s", "format": "%s"}""".formatted(content, format);
        }
    }
    public static class HtmlRenderer implements Renderer {
        public String render(String content, String format) {
            return "<div class=\"%s\">%s</div>".formatted(format, content);
        }
    }

    public abstract static class AlgorithmVisualizer {
        protected final Renderer renderer;
        protected AlgorithmVisualizer(Renderer renderer) { this.renderer = renderer; }
        public abstract String visualize(int[] steps);
    }

    public static class StepByStepVisualizer extends AlgorithmVisualizer {
        public StepByStepVisualizer(Renderer renderer) { super(renderer); }
        public String visualize(int[] steps) {
            return renderer.render(Arrays.toString(steps), "steps");
        }
    }

    // ── Composite ─────────────────────────────────────────────────────────────

    public interface AlgorithmComponent {
        String describe();
        int estimateComplexity();
    }

    public static class AtomicAlgorithm implements AlgorithmComponent {
        private final String name;
        private final int complexity;
        public AtomicAlgorithm(String name, int complexity) {
            this.name = name; this.complexity = complexity;
        }
        public String describe() { return name; }
        public int estimateComplexity() { return complexity; }
    }

    public static class CompositeAlgorithm implements AlgorithmComponent {
        private final String name;
        private final List<AlgorithmComponent> children = new ArrayList<>();

        public CompositeAlgorithm(String name) { this.name = name; }
        public void add(AlgorithmComponent c) { children.add(c); }
        public String describe() {
            return name + " -> [" + children.stream()
                .map(AlgorithmComponent::describe)
                .reduce((a, b) -> a + ", " + b).orElse("") + "]";
        }
        public int estimateComplexity() {
            return children.stream().mapToInt(AlgorithmComponent::estimateComplexity).sum();
        }
    }

    // ── Decorator ─────────────────────────────────────────────────────────────

    public interface AlgorithmExecutor {
        int[] execute(int[] input);
    }

    public static class TimingDecorator implements AlgorithmExecutor {
        private final AlgorithmExecutor wrapped;
        private long lastExecutionNs;

        public TimingDecorator(AlgorithmExecutor wrapped) { this.wrapped = wrapped; }

        @Override
        public int[] execute(int[] input) {
            long start = System.nanoTime();
            int[] result = wrapped.execute(input);
            lastExecutionNs = System.nanoTime() - start;
            return result;
        }
        public long lastExecutionNs() { return lastExecutionNs; }
    }

    public static class LoggingDecorator implements AlgorithmExecutor {
        private final AlgorithmExecutor wrapped;
        private final List<String> logs = new ArrayList<>();

        public LoggingDecorator(AlgorithmExecutor wrapped) { this.wrapped = wrapped; }

        @Override
        public int[] execute(int[] input) {
            logs.add("Input: " + Arrays.toString(input));
            int[] result = wrapped.execute(input);
            logs.add("Output: " + Arrays.toString(result));
            return result;
        }
        public List<String> logs() { return Collections.unmodifiableList(logs); }
    }

    // ── Facade ────────────────────────────────────────────────────────────────

    public static class AlgorithmFacade {
        private final com.conceptualware.core.algorithms.sorting.SortingAlgorithms sorting;
        private final com.conceptualware.core.algorithms.string.StringAlgorithms string;
        private final com.conceptualware.core.algorithms.dp.DynamicProgramming dp;

        public AlgorithmFacade() {
            this.sorting = new com.conceptualware.core.algorithms.sorting.SortingAlgorithms();
            this.string = new com.conceptualware.core.algorithms.string.StringAlgorithms();
            this.dp = new com.conceptualware.core.algorithms.dp.DynamicProgramming();
        }

        public int[] quickSort(int[] arr) {
            return com.conceptualware.core.algorithms.sorting.SortingAlgorithms.quickSort(arr);
        }
        public List<Integer> findPattern(String text, String pattern) {
            return com.conceptualware.core.algorithms.string.StringAlgorithms.kmpSearch(text, pattern);
        }
        public int minCoins(int[] coins, int amount) {
            return com.conceptualware.core.algorithms.dp.DynamicProgramming.coinChange(coins, amount);
        }
    }

    // ── Flyweight ─────────────────────────────────────────────────────────────

    public record ComplexityLabel(String bigO, String description) {}

    public static class ComplexityFlyweightFactory {
        private static final Map<String, ComplexityLabel> pool = new HashMap<>();

        public static ComplexityLabel get(String bigO, String description) {
            return pool.computeIfAbsent(bigO, k -> new ComplexityLabel(k, description));
        }
    }

    // ── Proxy ─────────────────────────────────────────────────────────────────

    public interface AlgorithmService {
        int[] sort(String algorithmName, int[] arr);
    }

    public static class RealAlgorithmService implements AlgorithmService {
        public int[] sort(String algorithmName, int[] arr) {
            return switch (algorithmName.toLowerCase()) {
                case "merge" -> com.conceptualware.core.algorithms.sorting.SortingAlgorithms.mergeSort(arr);
                case "quick" -> com.conceptualware.core.algorithms.sorting.SortingAlgorithms.quickSort(arr);
                default      -> com.conceptualware.core.algorithms.sorting.SortingAlgorithms.mergeSort(arr);
            };
        }
    }

    public static class CachingProxyAlgorithmService implements AlgorithmService {
        private final AlgorithmService real = new RealAlgorithmService();
        private final Map<String, int[]> cache = new ConcurrentHashMap<>();

        public int[] sort(String algorithmName, int[] arr) {
            String key = algorithmName + ":" + Arrays.hashCode(arr);
            return cache.computeIfAbsent(key, k -> real.sort(algorithmName, arr));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMPORTAMENTAIS
    // ══════════════════════════════════════════════════════════════════════════

    // ── Chain of Responsibility ───────────────────────────────────────────────

    public interface ValidationHandler {
        void setNext(ValidationHandler next);
        boolean handle(int[] input);
    }

    public abstract static class BaseValidationHandler implements ValidationHandler {
        protected ValidationHandler next;
        public void setNext(ValidationHandler next) { this.next = next; }
        protected boolean passToNext(int[] input) { return next == null || next.handle(input); }
    }

    public static class NullCheckHandler extends BaseValidationHandler {
        public boolean handle(int[] input) {
            if (input == null) throw new IllegalArgumentException("Input is null");
            return passToNext(input);
        }
    }

    public static class SizeCheckHandler extends BaseValidationHandler {
        private final int maxSize;
        public SizeCheckHandler(int maxSize) { this.maxSize = maxSize; }
        public boolean handle(int[] input) {
            if (input.length > maxSize) throw new IllegalArgumentException("Input too large");
            return passToNext(input);
        }
    }

    // ── Command ───────────────────────────────────────────────────────────────

    public interface Command { void execute(); void undo(); }

    public static class SortCommand implements Command {
        private final int[] original;
        private int[] sorted;
        private final String algorithm;
        private final Consumer<int[]> onResult;

        public SortCommand(int[] arr, String algorithm, Consumer<int[]> onResult) {
            this.original = arr.clone();
            this.algorithm = algorithm;
            this.onResult = onResult;
        }

        @Override
        public void execute() {
            sorted = com.conceptualware.core.algorithms.sorting.SortingAlgorithms.mergeSort(original);
            onResult.accept(sorted);
        }

        @Override
        public void undo() { onResult.accept(original); }
    }

    public static class CommandHistory {
        private final Deque<Command> history = new ArrayDeque<>();

        public void execute(Command cmd) { cmd.execute(); history.push(cmd); }
        public void undo() { if (!history.isEmpty()) history.pop().undo(); }
    }

    // ── Observer / Pub-Sub ────────────────────────────────────────────────────

    public interface AlgorithmEventListener {
        void onStepExecuted(int step, int[] currentState);
        void onCompleted(int[] finalResult, long durationNs);
    }

    public static class AlgorithmEventBus {
        private final List<AlgorithmEventListener> listeners = new CopyOnWriteArrayList<>();

        public void subscribe(AlgorithmEventListener listener) { listeners.add(listener); }
        public void unsubscribe(AlgorithmEventListener listener) { listeners.remove(listener); }

        public void notifyStep(int step, int[] state) {
            listeners.forEach(l -> l.onStepExecuted(step, state));
        }
        public void notifyCompleted(int[] result, long durationNs) {
            listeners.forEach(l -> l.onCompleted(result, durationNs));
        }
    }

    // ── Strategy ─────────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface SortStrategy { int[] sort(int[] arr); }

    public static class SortContext {
        private SortStrategy strategy;
        public SortContext(SortStrategy strategy) { this.strategy = strategy; }
        public void setStrategy(SortStrategy strategy) { this.strategy = strategy; }
        public int[] sort(int[] arr) { return strategy.sort(arr); }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    public enum ExecutionState { IDLE, RUNNING, PAUSED, COMPLETED, ERROR }

    public static class AlgorithmExecutionContext {
        private ExecutionState state = ExecutionState.IDLE;
        private int[] currentResult;

        public void start()  {
            if (state == ExecutionState.IDLE || state == ExecutionState.PAUSED)
                state = ExecutionState.RUNNING;
        }
        public void pause()  { if (state == ExecutionState.RUNNING) state = ExecutionState.PAUSED; }
        public void complete(int[] result) { currentResult = result; state = ExecutionState.COMPLETED; }
        public void error()  { state = ExecutionState.ERROR; }
        public ExecutionState getState() { return state; }
        public int[] getResult() { return currentResult; }
    }

    // ── Template Method ───────────────────────────────────────────────────────

    public abstract static class AlgorithmTemplate {
        // Template method — defines the algorithm skeleton
        public final int[] run(int[] input) {
            validate(input);
            int[] preprocessed = preprocess(input);
            int[] result = execute(preprocessed);
            return postprocess(result);
        }

        protected void validate(int[] input) {
            if (input == null) throw new IllegalArgumentException("Null input");
        }
        protected int[] preprocess(int[] input) { return input.clone(); }
        protected abstract int[] execute(int[] input);
        protected int[] postprocess(int[] result) { return result; }
    }

    public static class MergeSortTemplate extends AlgorithmTemplate {
        @Override
        protected int[] execute(int[] input) {
            return com.conceptualware.core.algorithms.sorting.SortingAlgorithms.mergeSort(input);
        }
    }

    // ── Memento ───────────────────────────────────────────────────────────────

    public static class AlgorithmMemento {
        private final int[] state;
        private final int step;
        AlgorithmMemento(int[] state, int step) { this.state = state.clone(); this.step = step; }
        public int[] getState() { return state.clone(); }
        public int getStep()    { return step; }
    }

    public static class AlgorithmCaretaker {
        private final Deque<AlgorithmMemento> history = new ArrayDeque<>();
        public void save(AlgorithmMemento m)  { history.push(m); }
        public AlgorithmMemento restore()     { return history.isEmpty() ? null : history.pop(); }
        public int historySize()              { return history.size(); }
    }

    // ── Visitor ───────────────────────────────────────────────────────────────

    public interface AlgorithmVisitor<T> {
        T visitSort(Sortable sorter);
        T visitSearch(SearchAlgorithmVisitable searcher);
    }

    public interface Visitable { <T> T accept(AlgorithmVisitor<T> visitor); }
    public interface SearchAlgorithmVisitable extends Visitable { List<Integer> search(String text, String pattern); }

    // ── Mediator ─────────────────────────────────────────────────────────────

    public interface Colleague { void setMediator(AlgorithmMediator mediator); }

    public interface AlgorithmMediator {
        void notify(Object sender, String event, Object data);
    }

    // ── Iterator ─────────────────────────────────────────────────────────────

    public static class AlgorithmStepIterator implements Iterator<int[]> {
        private final int[] arr;
        private int step = 0;
        private final List<int[]> steps;

        public AlgorithmStepIterator(int[] arr) {
            this.arr = arr.clone();
            this.steps = generateBubbleSortSteps(this.arr);
        }

        private List<int[]> generateBubbleSortSteps(int[] a) {
            List<int[]> result = new ArrayList<>();
            result.add(a.clone());
            for (int i = 0; i < a.length - 1; i++) {
                for (int j = 0; j < a.length - i - 1; j++) {
                    if (a[j] > a[j + 1]) {
                        int tmp = a[j]; a[j] = a[j + 1]; a[j + 1] = tmp;
                        result.add(a.clone());
                    }
                }
            }
            return result;
        }

        @Override
        public boolean hasNext() { return step < steps.size(); }

        @Override
        public int[] next() {
            if (!hasNext()) throw new NoSuchElementException();
            return steps.get(step++).clone();
        }
    }

    // ── Null Object ────────────────────────────────────────────────────────────

    public static class NullAlgorithmExecutor implements AlgorithmExecutor {
        @Override
        public int[] execute(int[] input) { return input; } // no-op
    }

    // ── Interpreter (mini-DSL for complexity notation) ────────────────────────

    public interface ComplexityExpression { double evaluate(int n); }

    public record ConstantExpression(double value) implements ComplexityExpression {
        public double evaluate(int n) { return value; }
    }
    public record LinearExpression(double coeff) implements ComplexityExpression {
        public double evaluate(int n) { return coeff * n; }
    }
    public record NLogNExpression(double coeff) implements ComplexityExpression {
        public double evaluate(int n) { return coeff * n * (Math.log(n) / Math.log(2)); }
    }
    public record QuadraticExpression(double coeff) implements ComplexityExpression {
        public double evaluate(int n) { return coeff * n * n; }
    }
    public record SumExpression(ComplexityExpression left, ComplexityExpression right) implements ComplexityExpression {
        public double evaluate(int n) { return left.evaluate(n) + right.evaluate(n); }
    }
}
