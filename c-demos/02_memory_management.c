/**
 * Concept — Manual Memory Management: malloc, free, memory errors (C)
 *
 * In C, the programmer owns the heap:
 *   malloc(n)    — allocate n bytes, return void* (or NULL on failure)
 *   calloc(n,sz) — allocate n*sz bytes, ZEROED
 *   realloc(p,n) — resize existing allocation
 *   free(p)      — release memory back to allocator
 *
 * Common Memory Bugs (with educational demonstrations):
 *   1. Use-After-Free (dangling pointer)
 *   2. Double Free
 *   3. Memory Leak
 *   4. Buffer Overflow (stack and heap)
 *   5. Reading Uninitialized Memory
 *   6. Null Dereference
 *
 * Tools for finding these bugs:
 *   Valgrind:   detects leaks, use-after-free, uninitialized reads at runtime
 *   AddressSanitizer (ASan): compile with -fsanitize=address for fast detection
 *   Electric Fence: page-protect heap to catch overflows immediately
 *
 * Compile: gcc -Wall -Wextra -g -fsanitize=address -o 02_memory 02_memory_management.c && ./02_memory
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ── Safe allocation helper ───────────────────────────────────────────────── */

void *safe_malloc(size_t size) {
    void *p = malloc(size);
    if (p == NULL) {
        fprintf(stderr, "FATAL: malloc(%zu) failed — out of memory\n", size);
        exit(EXIT_FAILURE);
    }
    return p;
}

/* ── malloc / free basics ─────────────────────────────────────────────────── */

void malloc_free_demo(void) {
    printf("\n═══ malloc / free Basics ═══\n");

    /* Allocate array of 5 ints on the HEAP */
    int *arr = (int*)safe_malloc(5 * sizeof(int));
    printf("  Allocated %zu bytes at %p\n", 5 * sizeof(int), (void*)arr);

    for (int i = 0; i < 5; i++) arr[i] = i * i;
    printf("  arr = ");
    for (int i = 0; i < 5; i++) printf("%d ", arr[i]);
    printf("\n");

    free(arr);              /* Release memory — MUST call when done */
    arr = NULL;             /* Best practice: NULL the pointer to prevent dangling use */
    printf("  Memory freed. arr set to NULL.\n");

    /* calloc: allocate + zero-initialize */
    double *zeros = (double*)calloc(4, sizeof(double));
    printf("  calloc(4, double) = [%.1f %.1f %.1f %.1f] (all zeros)\n",
           zeros[0], zeros[1], zeros[2], zeros[3]);
    free(zeros);

    /* realloc: grow existing allocation */
    int *v = (int*)safe_malloc(3 * sizeof(int));
    v[0]=1; v[1]=2; v[2]=3;
    v = (int*)realloc(v, 6 * sizeof(int));  /* expand from 3 to 6 ints */
    v[3]=4; v[4]=5; v[5]=6;
    printf("  After realloc to 6: ");
    for (int i=0;i<6;i++) printf("%d ", v[i]);
    printf("\n");
    free(v);
}

/* ── Dynamic Array (Vector) ──────────────────────────────────────────────── */

typedef struct {
    int    *data;
    size_t  size;
    size_t  capacity;
} DynArray;

void da_init(DynArray *da) {
    da->capacity = 4;
    da->size = 0;
    da->data = (int*)safe_malloc(da->capacity * sizeof(int));
}

void da_push(DynArray *da, int val) {
    if (da->size == da->capacity) {
        da->capacity *= 2;  /* double capacity — O(1) amortized */
        da->data = (int*)realloc(da->data, da->capacity * sizeof(int));
    }
    da->data[da->size++] = val;
}

void da_free(DynArray *da) { free(da->data); da->data = NULL; da->size = da->capacity = 0; }

void dynamic_array_demo(void) {
    printf("\n═══ Dynamic Array (heap-managed) ═══\n");

    DynArray da;
    da_init(&da);
    for (int i = 0; i < 10; i++) {
        da_push(&da, i * 3);
        printf("  push(%d): size=%zu capacity=%zu\n", i*3, da.size, da.capacity);
    }
    da_free(&da);
}

/* ── Dangling Pointer ────────────────────────────────────────────────────── */

void dangling_pointer_demo(void) {
    printf("\n═══ Dangling Pointer (Educational Demo) ═══\n");

    int *p = (int*)safe_malloc(sizeof(int));
    *p = 42;
    printf("  Before free: *p = %d\n", *p);

    free(p);
    /* p is now a DANGLING POINTER — points to freed memory */
    /* Accessing *p after free is Undefined Behavior (UB) */
    /* The memory may be reallocated to something else */

    printf("  [SAFE] After free, p = %p (dangling — but we don't dereference)\n", (void*)p);

    /* FIX: Always NULL the pointer after freeing */
    p = NULL;
    if (p == NULL) printf("  [FIXED] p = NULL — safe to check before use\n");

    /* ── Use-after-free example (for educational display ONLY) ── */
    int *q = (int*)safe_malloc(sizeof(int));
    *q = 99;
    int snapshot = *q;  /* save before free */
    free(q);
    q = NULL;
    printf("  Saved value before free: %d (never access freed memory!)\n", snapshot);
}

/* ── Memory Leak ──────────────────────────────────────────────────────────── */

void leaky_function(void) {
    int *p = (int*)safe_malloc(1024 * sizeof(int));
    /* ERROR: forgot to free(p) — memory leaked on every call */
    (void)p; /* suppress unused warning in this demo */
    /* In production: Valgrind reports "1024 bytes in 1 block definitely lost" */
}

void no_leak_function(void) {
    int *p = (int*)safe_malloc(1024 * sizeof(int));
    /* use p... */
    free(p);  /* always paired with malloc */
}

void memory_leak_demo(void) {
    printf("\n═══ Memory Leak Demo ═══\n");
    printf("  leaky_function() allocates but never frees — 1 leak per call\n");
    printf("  In production: use Valgrind: valgrind --leak-check=full ./program\n");
    printf("  Or: gcc -fsanitize=address — prints leak report on exit\n");
    no_leak_function();
    printf("  no_leak_function() properly pairs malloc/free ✓\n");
}

/* ── Stack vs Heap ────────────────────────────────────────────────────────── */

void stack_vs_heap(void) {
    printf("\n═══ Stack vs Heap Memory ═══\n");

    /* Stack allocation — automatic, LIFO, limited size (typically 1-8 MB) */
    int stack_array[100];  /* 400 bytes on stack */
    printf("  Stack array address: %p (grows downward on x86)\n", (void*)stack_array);
    printf("  Stack allocation: automatic, freed when function returns\n");
    printf("  Stack overflow: recursive functions without base case → SIGSEGV\n");

    /* Heap allocation — manual, can be large, survives function return */
    int *heap_array = (int*)safe_malloc(100 * sizeof(int));
    printf("  Heap array address:  %p (grows upward)\n", (void*)heap_array);
    printf("  Heap allocation: manual, must free() explicitly\n");
    free(heap_array);

    /* Key difference: stack vars die when function returns */
    /* NEVER return pointer to local (stack) variable: */
    /* int* bad_function() { int x = 42; return &x; } ← DANGLING! */
}

/* ── Buffer Overflow (Stack) ──────────────────────────────────────────────── */

void buffer_overflow_demo(void) {
    printf("\n═══ Buffer Overflow (Stack) ═══\n");

    char safe[8];
    /* SAFE: strncpy with explicit limit — copy at most sizeof(safe)-1 bytes */
    strncpy(safe, "Hello!!", sizeof(safe) - 1);
    safe[sizeof(safe) - 1] = '\0'; /* ensure null terminator */
    printf("  Safe copy: \"%s\"\n", safe);

    /* UNSAFE (DO NOT DO IN PRODUCTION):
       char buf[8];
       strcpy(buf, "This string is way too long!!!"); // ← stack overflow
       Overwrites: saved frame pointer, return address → arbitrary code execution
    */
    printf("  UNSAFE: strcpy without bounds check → stack smashing\n");
    printf("  MITIGATION: Stack Canaries (-fstack-protector), ASLR, NX bit\n");
    printf("  SAFE alternatives: strncpy, strlcpy, snprintf, fgets\n");
}

/* ── Heap Buffer Overflow ─────────────────────────────────────────────────── */

void heap_overflow_demo(void) {
    printf("\n═══ Buffer Overflow (Heap) ═══\n");

    char *buf = (char*)safe_malloc(8);
    /* SAFE write */
    memcpy(buf, "Hi!", 3);
    buf[3] = '\0';
    printf("  Heap buffer content: \"%s\"\n", buf);

    /* UNSAFE (for demo — DO NOT DO):
       memcpy(buf, "This is definitely more than 8 bytes", 36);
       ↑ Corrupts heap metadata → future malloc/free crashes or exploitable
    */
    printf("  HEAP OVERFLOW: corrupts allocator metadata → crash or exploit\n");
    printf("  DETECTION: valgrind, ASan, heap canaries\n");

    free(buf);
}

/* ── Linked List with manual memory ──────────────────────────────────────── */

typedef struct Node {
    int         value;
    struct Node *next;
} Node;

Node* list_prepend(Node *head, int val) {
    Node *node = (Node*)safe_malloc(sizeof(Node));
    node->value = val;
    node->next  = head;
    return node;
}

void list_free(Node *head) {
    while (head) {
        Node *next = head->next;
        free(head);
        head = next;
    }
}

void linked_list_demo(void) {
    printf("\n═══ Linked List (Manual Heap Allocation) ═══\n");

    Node *list = NULL;
    for (int i = 5; i >= 1; i--) list = list_prepend(list, i);

    printf("  List: ");
    for (Node *p = list; p != NULL; p = p->next) printf("%d ", p->value);
    printf("\n");

    list_free(list);
    list = NULL;
    printf("  All nodes freed ✓\n");
}

int main(void) {
    printf("╔══════════════════════════════════════════════════╗\n");
    printf("║   Concept Demo: Manual Memory Management (C)     ║\n");
    printf("╚══════════════════════════════════════════════════╝\n");

    malloc_free_demo();
    dynamic_array_demo();
    dangling_pointer_demo();
    memory_leak_demo();
    stack_vs_heap();
    buffer_overflow_demo();
    heap_overflow_demo();
    linked_list_demo();

    printf("\n✓ All demos completed. Run with Valgrind to verify no leaks.\n");
    return 0;
}
