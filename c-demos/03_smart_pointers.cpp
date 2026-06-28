/**
 * Concept — Smart Pointers & RAII (C++11/14/17)
 *
 * Smart pointers wrap raw pointers and automatically manage memory lifetime.
 * They implement RAII: Resource Acquisition Is Initialization.
 *
 * THREE SMART POINTERS:
 *
 *   unique_ptr<T>:
 *     - EXCLUSIVE ownership: only one owner at a time
 *     - Non-copyable, but MOVABLE (ownership transfer)
 *     - Destroyed when it goes out of scope (RAII)
 *     - Zero overhead vs raw pointer in release builds
 *     - Use case: factory functions, class members
 *
 *   shared_ptr<T>:
 *     - SHARED ownership: reference-counted
 *     - Destroyed when last shared_ptr pointing to it goes out of scope
 *     - Thread-safe ref count increment/decrement (atomic)
 *     - Overhead: heap-allocated control block (ref count + deleter)
 *     - Use case: shared resources, graph nodes, callbacks
 *
 *   weak_ptr<T>:
 *     - NON-OWNING reference to shared_ptr managed object
 *     - Does NOT increment reference count
 *     - Must lock() to access (returns shared_ptr or nullptr if expired)
 *     - Breaks circular reference cycles (which cause memory leaks with shared_ptr)
 *     - Use case: observer pattern, caches, parent→child in tree structures
 *
 * Compile: g++ -std=c++17 -Wall -o 03_smart_pointers 03_smart_pointers.cpp && ./03_smart_pointers
 */

#include <iostream>
#include <memory>
#include <string>
#include <vector>
#include <cassert>

// ── unique_ptr ──────────────────────────────────────────────────────────────

class Resource {
    std::string name_;
public:
    explicit Resource(std::string name) : name_(std::move(name)) {
        std::cout << "    [CONSTRUCT] Resource(" << name_ << ")\n";
    }
    ~Resource() {
        std::cout << "    [DESTROY]   Resource(" << name_ << ")\n";
    }
    void use() const { std::cout << "    [USE]       Resource(" << name_ << ")\n"; }
    const std::string& name() const { return name_; }
};

void unique_ptr_demo() {
    std::cout << "\n═══ unique_ptr (exclusive ownership) ═══\n";

    {   // Scope block
        auto p = std::make_unique<Resource>("Alpha");  // preferred over new
        p->use();

        // Move ownership: p is now empty
        auto q = std::move(p);
        std::cout << "  After move: p is " << (p ? "valid" : "nullptr") << "\n";
        q->use();
        std::cout << "  End of scope → q destroyed automatically:\n";
    }   // ← q goes out of scope → ~Resource("Alpha") called HERE

    std::cout << "  No explicit delete needed!\n";

    // unique_ptr with custom deleter
    auto deleter = [](Resource* r) {
        std::cout << "  [CUSTOM DELETER] cleaning up " << r->name() << "\n";
        delete r;
    };
    std::unique_ptr<Resource, decltype(deleter)> p2(new Resource("Beta"), deleter);
    // Custom deleter useful for: FILE* fclose, SDL_Surface SDL_FreeSurface, etc.
}

// ── unique_ptr for arrays ────────────────────────────────────────────────────

void unique_ptr_array() {
    std::cout << "\n═══ unique_ptr<T[]> (array ownership) ═══\n";

    auto arr = std::make_unique<int[]>(5);  // allocates int[5]
    for (int i = 0; i < 5; i++) arr[i] = i * i;
    std::cout << "  arr = ";
    for (int i = 0; i < 5; i++) std::cout << arr[i] << " ";
    std::cout << "\n  (freed automatically when arr goes out of scope)\n";
}

// ── shared_ptr ──────────────────────────────────────────────────────────────

void shared_ptr_demo() {
    std::cout << "\n═══ shared_ptr (reference counting) ═══\n";

    auto p1 = std::make_shared<Resource>("Gamma");
    std::cout << "  p1 use_count: " << p1.use_count() << "\n";  // 1

    {
        auto p2 = p1;  // copy → increment ref count
        std::cout << "  After p2=p1: use_count = " << p1.use_count() << "\n";  // 2

        auto p3 = p1;  // another copy
        std::cout << "  After p3=p1: use_count = " << p1.use_count() << "\n";  // 3

        p3->use();
        // p2, p3 go out of scope here → ref count decremented
        std::cout << "  End inner scope → p2,p3 destroyed:\n";
    }   // ← ref count back to 1

    std::cout << "  After inner scope: use_count = " << p1.use_count() << "\n";  // 1
    // p1 goes out of scope → ref count = 0 → Resource destroyed
    std::cout << "  End outer scope → p1 destroyed:\n";
}

// ── weak_ptr: breaking reference cycles ─────────────────────────────────────

struct Node {
    std::string name;
    std::shared_ptr<Node> next;   // strong ref — causes cycle if circular
    std::weak_ptr<Node>   prev;   // weak ref — does NOT prevent destruction

    explicit Node(std::string n) : name(std::move(n)) {
        std::cout << "    [CONSTRUCT] Node(" << name << ")\n";
    }
    ~Node() {
        std::cout << "    [DESTROY]   Node(" << name << ")\n";
    }
};

void weak_ptr_demo() {
    std::cout << "\n═══ weak_ptr (non-owning, breaks cycles) ═══\n";

    // Without weak_ptr: MEMORY LEAK from circular reference
    // A → B (strong), B → A (strong) → neither ever freed
    std::cout << "  Circular reference cycle demonstration:\n";
    {
        auto a = std::make_shared<Node>("A");
        auto b = std::make_shared<Node>("B");
        a->next = b;          // strong: A→B
        b->prev = a;          // WEAK:   B⇢A (weak_ptr)
        // With weak_ptr for prev: both nodes are freed at scope exit ✓
        // Without weak_ptr (b->next = a): NEITHER freed — memory leak
    }
    std::cout << "  (Both nodes destroyed — no cycle leak because prev is weak_ptr)\n";

    // Accessing a weak_ptr safely
    std::cout << "\n  weak_ptr lock() demo:\n";
    std::weak_ptr<Resource> weak;
    {
        auto strong = std::make_shared<Resource>("Delta");
        weak = strong;  // does NOT increase ref count
        std::cout << "  weak.expired() = " << weak.expired() << "\n";

        if (auto locked = weak.lock()) {  // lock() → shared_ptr or nullptr
            locked->use();
        }
        // strong goes out of scope → Resource destroyed
        std::cout << "  End of scope → Resource destroyed:\n";
    }
    std::cout << "  weak.expired() = " << weak.expired() << "\n";  // true now
    if (weak.lock() == nullptr) std::cout << "  lock() returned nullptr — safe!\n";
}

// ── RAII Pattern ─────────────────────────────────────────────────────────────

/**
 * RAII (Resource Acquisition Is Initialization):
 *   - Acquire resource in constructor
 *   - Release resource in destructor
 *   - Object's lifetime = resource lifetime
 *   - Works with ANY resource: memory, files, mutexes, network connections, GPU buffers
 *
 * Smart pointers implement RAII for heap memory.
 * std::lock_guard implements RAII for mutexes.
 * std::fstream implements RAII for file handles.
 */

class FileRAII {
    FILE *file_;
    std::string path_;
public:
    explicit FileRAII(const char *path, const char *mode) : path_(path) {
        file_ = fopen(path, mode);
        if (!file_) throw std::runtime_error("Cannot open: " + std::string(path));
        std::cout << "    [RAII] File opened: " << path << "\n";
    }
    ~FileRAII() {
        if (file_) { fclose(file_); std::cout << "    [RAII] File closed: " << path_ << "\n"; }
    }
    FILE* get() { return file_; }

    // Non-copyable (exclusive resource ownership)
    FileRAII(const FileRAII&) = delete;
    FileRAII& operator=(const FileRAII&) = delete;
};

class MutexRAII {
    bool locked_;
    std::string name_;
public:
    explicit MutexRAII(const std::string& name) : locked_(false), name_(name) {}
    void lock()   { locked_ = true;  std::cout << "    [RAII] Mutex '" << name_ << "' locked\n"; }
    void unlock() { locked_ = false; std::cout << "    [RAII] Mutex '" << name_ << "' unlocked\n"; }
    ~MutexRAII()  { if (locked_) unlock(); } // guarantee unlock even on exception
};

void raii_demo() {
    std::cout << "\n═══ RAII Pattern ═══\n";

    std::cout << "  File RAII (acquire in ctor, release in dtor):\n";
    try {
        FileRAII f("/tmp/raii_test.txt", "w");
        fputs("Hello RAII!\n", f.get());
        // f goes out of scope → file closed automatically (even if exception thrown)
    } catch (const std::exception &e) {
        std::cout << "  File error (expected on Windows): " << e.what() << "\n";
    }

    std::cout << "\n  Mutex RAII (never forget to unlock):\n";
    {
        MutexRAII mtx("critical_section");
        mtx.lock();
        // critical section... even if exception here, dtor unlocks
    }  // ← dtor calls unlock automatically

    std::cout << "\n  RAII benefits:\n";
    std::cout << "    ✓ No resource leaks (destructor always runs)\n";
    std::cout << "    ✓ Exception safe (destructor runs on stack unwind)\n";
    std::cout << "    ✓ Composable (RAII objects contain RAII members)\n";
}

// ── Copy-on-Write (CoW) ──────────────────────────────────────────────────────

/**
 * Copy-on-Write: share data until one copy needs to mutate.
 * Optimization: defer expensive copy until actually needed.
 * Used in: std::string (old GCC), fork() process creation, Redis persistence.
 */

class CowString {
    std::shared_ptr<std::string> data_;

public:
    explicit CowString(std::string s) : data_(std::make_shared<std::string>(std::move(s))) {}

    // Cheap copy: just increment ref count (no string copy)
    CowString(const CowString&) = default;

    // On mutation: detach if shared (copy-on-write)
    void append(const std::string& suffix) {
        if (data_.use_count() > 1) {           // others share this data
            data_ = std::make_shared<std::string>(*data_);  // deep copy NOW
            std::cout << "    [CoW] Triggered copy — was shared, now exclusive\n";
        }
        *data_ += suffix;
    }

    const std::string& str() const { return *data_; }
    long use_count() const { return data_.use_count(); }
};

void copy_on_write_demo() {
    std::cout << "\n═══ Copy-on-Write ═══\n";

    CowString s1("Hello");
    CowString s2 = s1;           // cheap copy (shared data)
    CowString s3 = s1;           // cheap copy (shared data)
    std::cout << "  After 3 copies, use_count = " << s1.use_count() << "\n";

    std::cout << "  Mutating s2:\n";
    s2.append(", World!");       // CoW triggered here
    std::cout << "  s1 = \"" << s1.str() << "\" (unchanged)\n";
    std::cout << "  s2 = \"" << s2.str() << "\"\n";
}

// ── False Sharing ─────────────────────────────────────────────────────────────

/**
 * False Sharing: performance problem in multi-threaded code.
 * CPU cache lines are typically 64 bytes.
 * If two threads write to different variables on the SAME cache line,
 * the cache line "ping-pongs" between CPU cores → severe slowdown.
 *
 * Solution: pad data to cache line boundaries.
 *   alignas(64) or std::hardware_destructive_interference_size
 */

struct FalseSharing_BAD {
    int counter_A;   // thread 1 writes here
    int counter_B;   // thread 2 writes here — SAME cache line → false sharing!
};

struct FalseSharing_GOOD {
    alignas(64) int counter_A;   // padded to own cache line
    alignas(64) int counter_B;   // padded to own cache line
};

void false_sharing_demo() {
    std::cout << "\n═══ False Sharing & Cache Alignment ═══\n";
    std::cout << "  FalseSharing_BAD size:  " << sizeof(FalseSharing_BAD)  << " bytes\n";
    std::cout << "  FalseSharing_GOOD size: " << sizeof(FalseSharing_GOOD) << " bytes\n";
    std::cout << "  CPU cache line size:    64 bytes (typical)\n";
    std::cout << "  BAD:  counter_A and counter_B share cache line → false sharing\n";
    std::cout << "  GOOD: each counter on its own cache line → no false sharing\n";
    std::cout << "  Impact: false sharing can cause 5-10x slowdown in hot paths\n";
}

// ── Memory Layout: BSS, Data, Text, Stack, Heap ──────────────────────────────

int global_initialized = 42;           // DATA segment (initialized global)
int global_uninitialized;              // BSS segment (zero-initialized, no space in binary)
const char *string_literal = "hello";  // TEXT (rodata) segment

void memory_layout_demo() {
    std::cout << "\n═══ Memory Layout: Segments ═══\n";

    int stack_var = 10;                // STACK
    int *heap_var = new int(20);       // HEAP

    std::cout << "  TEXT (code) segment: machine instructions (read+execute, no-write)\n";
    std::cout << "  DATA segment: initialized globals — e.g., global_initialized=" << global_initialized << "\n";
    std::cout << "  BSS segment:  zero-initialized globals — e.g., global_uninitialized=" << global_uninitialized << "\n";
    std::cout << "  STACK: local variables, function frames — stack_var=" << stack_var << "\n";
    std::cout << "  HEAP:  dynamic allocation — *heap_var=" << *heap_var << "\n\n";

    std::cout << "  Addresses (typical x86-64 Linux layout):\n";
    std::cout << "    TEXT  (rodata): " << (void*)string_literal << "\n";
    std::cout << "    DATA:           " << (void*)&global_initialized << "\n";
    std::cout << "    BSS:            " << (void*)&global_uninitialized << "\n";
    std::cout << "    STACK:          " << (void*)&stack_var << "\n";
    std::cout << "    HEAP:           " << (void*)heap_var << "\n";

    delete heap_var;
}

int main() {
    std::cout << "╔══════════════════════════════════════════════════╗\n";
    std::cout << "║  Concept Demo: Smart Pointers, RAII, CoW (C++)   ║\n";
    std::cout << "╚══════════════════════════════════════════════════╝\n";

    unique_ptr_demo();
    unique_ptr_array();
    shared_ptr_demo();
    weak_ptr_demo();
    raii_demo();
    copy_on_write_demo();
    false_sharing_demo();
    memory_layout_demo();

    std::cout << "\n✓ All demos completed — no memory leaks!\n";
    return 0;
}
