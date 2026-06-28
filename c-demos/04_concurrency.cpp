/**
 * C/C++ Concept — Concurrency & Memory Model
 *
 * Demonstrates:
 *   - std::thread, std::mutex, std::condition_variable
 *   - std::atomic and memory ordering (seq_cst, acquire/release, relaxed)
 *   - Lock-free data structures (Treiber stack)
 *   - Thread pool pattern
 *   - Race condition detection
 *   - std::promise / std::future (async results)
 *   - C++ memory model: happens-before, synchronizes-with
 *
 * Build: g++ -std=c++17 -Wall -pthread -O2 -o 04_concurrency 04_concurrency.cpp
 */

#include <iostream>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <vector>
#include <queue>
#include <future>
#include <functional>
#include <cassert>
#include <chrono>
#include <memory>

using namespace std;
using namespace std::chrono_literals;

// ─────────────────────────────────────────────────────────────────────────────
// 1. Race condition demo
// ─────────────────────────────────────────────────────────────────────────────

void race_condition_demo() {
    cout << "\n=== 1. Race Condition ===\n";

    // BAD: concurrent increments without synchronization
    int counter_bad = 0;
    auto increment_unsafe = [&]() {
        for (int i = 0; i < 10000; i++) counter_bad++;  // data race!
    };
    thread t1(increment_unsafe), t2(increment_unsafe);
    t1.join(); t2.join();
    cout << "Unsafe counter (expected 20000, got): " << counter_bad << "\n";
    // Result is non-deterministic — often < 20000 due to lost updates

    // GOOD: mutex-protected counter
    int counter_safe = 0;
    mutex mtx;
    auto increment_safe = [&]() {
        for (int i = 0; i < 10000; i++) {
            lock_guard<mutex> lock(mtx);  // RAII lock — unlocks on scope exit
            counter_safe++;
        }
    };
    thread t3(increment_safe), t4(increment_safe);
    t3.join(); t4.join();
    cout << "Safe counter (expected 20000, got): " << counter_safe << "\n";
    assert(counter_safe == 20000);

    // BEST: atomic counter (lock-free, faster than mutex for simple increments)
    atomic<int> counter_atomic{0};
    auto increment_atomic = [&]() {
        for (int i = 0; i < 10000; i++) counter_atomic.fetch_add(1, memory_order_relaxed);
    };
    thread t5(increment_atomic), t6(increment_atomic);
    t5.join(); t6.join();
    cout << "Atomic counter (expected 20000, got): " << counter_atomic.load() << "\n";
    assert(counter_atomic.load() == 20000);
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Producer-Consumer with condition_variable
// ─────────────────────────────────────────────────────────────────────────────

void producer_consumer_demo() {
    cout << "\n=== 2. Producer-Consumer (condition_variable) ===\n";

    // Bounded buffer shared between producer and consumer
    const int BUFFER_SIZE = 5;
    queue<int>         buffer;
    mutex              mtx;
    condition_variable not_full, not_empty;
    bool               done = false;
    int                total_produced = 0, total_consumed = 0;

    auto producer = [&]() {
        for (int i = 1; i <= 20; i++) {
            unique_lock<mutex> lock(mtx);
            // Wait while buffer full
            not_full.wait(lock, [&]{ return (int)buffer.size() < BUFFER_SIZE; });
            buffer.push(i);
            total_produced++;
            not_empty.notify_one();  // wake consumer
        }
        {
            lock_guard<mutex> lock(mtx);
            done = true;
        }
        not_empty.notify_all();  // wake consumer so it can exit
    };

    auto consumer = [&]() {
        while (true) {
            unique_lock<mutex> lock(mtx);
            not_empty.wait(lock, [&]{ return !buffer.empty() || done; });
            if (buffer.empty()) break;  // done and empty
            int val = buffer.front(); buffer.pop();
            total_consumed++;
            not_full.notify_one();   // wake producer
        }
    };

    thread prod(producer), cons(consumer);
    prod.join(); cons.join();

    cout << "Produced: " << total_produced
         << ", Consumed: " << total_consumed << "\n";
    assert(total_produced == total_consumed);
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. C++ Memory Model — atomic memory ordering
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Memory orderings control how CPU/compiler may reorder operations:
 *
 *   relaxed:      no ordering guarantee beyond atomicity (counter stats)
 *   acquire:      load — no reads/writes before this can be reordered after it
 *   release:      store — no reads/writes after this can be reordered before it
 *   acq_rel:      both acquire and release (RMW operations like fetch_add)
 *   seq_cst:      (default) total order — slowest, strongest guarantee
 *
 * Acquire-Release idiom: common pattern for publish-subscribe
 *   Producer: data = value;  flag.store(true, release)  // "publish"
 *   Consumer: while (!flag.load(acquire));  use(data)   // "subscribe"
 *   Guarantee: consumer sees data write BEFORE flag write.
 */
void memory_ordering_demo() {
    cout << "\n=== 3. C++ Memory Model (acquire/release) ===\n";

    int data = 0;
    atomic<bool> ready{false};

    thread writer([&]() {
        data = 42;                          // (1) write data
        ready.store(true, memory_order_release);  // (2) publish
        // "release" ensures (1) happens-before any thread that "acquires" ready
    });

    thread reader([&]() {
        while (!ready.load(memory_order_acquire));  // spin-wait
        // "acquire" ensures we see all writes before the release store
        cout << "Reader sees data = " << data << " (expected 42)\n";
        assert(data == 42);
    });

    writer.join();
    reader.join();
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Lock-free stack (Treiber stack)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Treiber stack (1986): lock-free stack using compare-and-swap (CAS).
 *
 * CAS: atomic_val.compare_exchange_weak(expected, desired)
 *   Atomically: if (atomic_val == expected) { atomic_val = desired; return true; }
 *              else                          { expected = atomic_val; return false; }
 *
 * ABA Problem: CAS doesn't detect if value changed from A→B→A between reads.
 * Solution: tagged pointers (combine pointer + version counter in one word).
 *
 * This implementation uses shared_ptr to handle memory safely
 * (hazard pointers or epoch-based reclamation used in production).
 */
template<typename T>
class LockFreeStack {
    struct Node {
        T                          value;
        shared_ptr<Node>           next;
        explicit Node(T v) : value(std::move(v)) {}
    };

    atomic<shared_ptr<Node>> head;  // C++20 atomic<shared_ptr>

public:
    void push(T value) {
        auto newNode = make_shared<Node>(std::move(value));
        newNode->next = head.load();
        // CAS loop: retry if head changed while we were setting newNode->next
        while (!head.compare_exchange_weak(newNode->next, newNode));
    }

    optional<T> pop() {
        auto old = head.load();
        while (old && !head.compare_exchange_weak(old, old->next));
        if (old) return old->value;
        return nullopt;
    }

    bool empty() const { return head.load() == nullptr; }
};

void lock_free_stack_demo() {
    cout << "\n=== 4. Lock-Free Stack (Treiber Stack) ===\n";

    LockFreeStack<int> stack;

    // Multiple threads push concurrently
    vector<thread> writers;
    for (int t = 0; t < 4; t++) {
        writers.emplace_back([&stack, t]() {
            for (int i = 0; i < 25; i++) stack.push(t * 100 + i);
        });
    }
    for (auto& w : writers) w.join();

    // Count items
    int count = 0;
    while (!stack.empty()) {
        auto v = stack.pop();
        if (v) count++;
    }
    cout << "Pushed 100 items, popped " << count << " items\n";
    assert(count == 100);
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Thread Pool
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Thread pool: reuse a fixed set of threads to execute tasks.
 * Avoids thread creation overhead for short-lived tasks.
 *
 * Pattern:
 *   - N worker threads block on a task queue
 *   - submit() enqueues a packaged_task (function + future result)
 *   - Worker threads pop tasks and execute them
 *   - Caller waits on the returned future for the result
 */
class ThreadPool {
    vector<thread>         workers;
    queue<function<void()>> tasks;
    mutex                   queueMtx;
    condition_variable      cv;
    bool                    stop = false;

public:
    explicit ThreadPool(size_t numThreads) {
        for (size_t i = 0; i < numThreads; i++) {
            workers.emplace_back([this]() {
                while (true) {
                    function<void()> task;
                    {
                        unique_lock<mutex> lock(queueMtx);
                        cv.wait(lock, [this]{ return stop || !tasks.empty(); });
                        if (stop && tasks.empty()) return;
                        task = std::move(tasks.front());
                        tasks.pop();
                    }
                    task();  // execute outside the lock
                }
            });
        }
    }

    template<typename F, typename... Args>
    auto submit(F&& f, Args&&... args) -> future<invoke_result_t<F, Args...>> {
        using RetType = invoke_result_t<F, Args...>;
        auto task = make_shared<packaged_task<RetType()>>(
            bind(forward<F>(f), forward<Args>(args)...)
        );
        future<RetType> fut = task->get_future();
        {
            lock_guard<mutex> lock(queueMtx);
            if (stop) throw runtime_error("ThreadPool: submitted to stopped pool");
            tasks.emplace([task]() { (*task)(); });
        }
        cv.notify_one();
        return fut;
    }

    ~ThreadPool() {
        { lock_guard<mutex> lock(queueMtx); stop = true; }
        cv.notify_all();
        for (auto& w : workers) w.join();
    }
};

void thread_pool_demo() {
    cout << "\n=== 5. Thread Pool ===\n";

    ThreadPool pool(4);  // 4 worker threads

    vector<future<int>> futures;
    for (int i = 1; i <= 16; i++) {
        futures.push_back(pool.submit([i]() {
            this_thread::sleep_for(1ms);  // simulate work
            return i * i;
        }));
    }

    int sum = 0;
    for (auto& f : futures) sum += f.get();
    // 1² + 2² + ... + 16² = n(n+1)(2n+1)/6 = 16*17*33/6 = 1496
    cout << "Sum of squares 1..16 = " << sum << " (expected 1496)\n";
    assert(sum == 1496);
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. std::promise and std::future
// ─────────────────────────────────────────────────────────────────────────────

void promise_future_demo() {
    cout << "\n=== 6. std::promise / std::future ===\n";

    // promise: one-time channel to pass a value between threads
    promise<int> prom;
    future<int>  fut = prom.get_future();

    thread worker([&prom]() {
        this_thread::sleep_for(5ms);   // simulate async computation
        prom.set_value(42);            // send result to future
    });

    // Main thread can do other work, then wait for result
    cout << "Waiting for async result...\n";
    int result = fut.get();  // blocks until value is set
    cout << "Received: " << result << "\n";
    assert(result == 42);

    worker.join();

    // std::async: higher-level wrapper (automatically creates promise/future)
    auto asyncResult = async(launch::async, [](int x) { return x * x; }, 7);
    cout << "async(7*7) = " << asyncResult.get() << "\n";
    assert(asyncResult.get() == 49);
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. Readers-Writer Lock (shared_mutex)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * shared_mutex (C++17): multiple readers OR one exclusive writer.
 *
 *   shared_lock<shared_mutex>   — for readers (concurrent)
 *   unique_lock<shared_mutex>   — for writers (exclusive)
 *
 * Use when: reads are frequent and cheap; writes are rare and expensive.
 * Example: in-memory cache, configuration object, routing table.
 */
void readers_writer_demo() {
    cout << "\n=== 7. Readers-Writer Lock (shared_mutex) ===\n";

    int         data = 0;
    shared_mutex rwMutex;
    atomic<int>  readCount{0};

    // Readers run concurrently
    vector<thread> readers;
    for (int i = 0; i < 8; i++) {
        readers.emplace_back([&]() {
            for (int j = 0; j < 100; j++) {
                shared_lock<shared_mutex> lock(rwMutex);  // shared read lock
                volatile int val = data;  // read without modifying
                readCount.fetch_add(1, memory_order_relaxed);
                (void)val;
            }
        });
    }

    // Writer takes exclusive lock
    thread writer([&]() {
        for (int j = 0; j < 10; j++) {
            unique_lock<shared_mutex> lock(rwMutex);  // exclusive write lock
            data++;
        }
    });

    for (auto& r : readers) r.join();
    writer.join();

    cout << "Final data = " << data << " (expected 10)\n";
    cout << "Total reads = " << readCount.load() << " (expected 800)\n";
    assert(data == 10);
    assert(readCount.load() == 800);
}

// ─────────────────────────────────────────────────────────────────────────────
// main
// ─────────────────────────────────────────────────────────────────────────────

int main() {
    cout << "══════════════════════════════════════════════════════════\n";
    cout << " C++ Concurrency & Memory Model Demonstrations\n";
    cout << "══════════════════════════════════════════════════════════\n";

    race_condition_demo();
    producer_consumer_demo();
    memory_ordering_demo();
    lock_free_stack_demo();
    thread_pool_demo();
    promise_future_demo();
    readers_writer_demo();

    cout << "\n✓ All concurrency demonstrations completed.\n";
    return 0;
}
