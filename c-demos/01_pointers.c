/**
 * Concept — Pointers & Memory Addressing (C)
 *
 * A pointer is a variable that holds a MEMORY ADDRESS.
 * Understanding pointers is essential for:
 *   - Manual memory management (malloc/free)
 *   - Data structures (linked list, trees use pointer links)
 *   - Systems programming (OS, embedded, device drivers)
 *   - Understanding how Java references work under the hood
 *
 * Compile: gcc -Wall -Wextra -o 01_pointers 01_pointers.c && ./01_pointers
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

/* ── Basic Pointer Operations ─────────────────────────────────────────────── */

void basic_pointers(void) {
    printf("\n═══ Basic Pointer Operations ═══\n");

    int x = 42;
    int *p = &x;        /* & = address-of operator: p holds address of x */

    printf("  x       = %d\n", x);
    printf("  &x      = %p   (address of x)\n", (void*)&x);
    printf("  p       = %p   (p holds address of x)\n", (void*)p);
    printf("  *p      = %d   (* = dereference: read value at address)\n", *p);

    *p = 100;           /* Write through pointer: modifies x */
    printf("  After *p=100: x = %d\n", x);

    /* Pointer arithmetic: pointers understand element size */
    int arr[5] = {10, 20, 30, 40, 50};
    int *q = arr;       /* array name decays to pointer to first element */

    printf("\n  Pointer arithmetic on int array:\n");
    for (int i = 0; i < 5; i++) {
        printf("    q+%d = %p, *(q+%d) = %d\n", i, (void*)(q+i), i, *(q+i));
    }
    /* q+1 adds sizeof(int) bytes (typically 4) to the address */
}

/* ── Pointer to Pointer ───────────────────────────────────────────────────── */

void double_pointer(void) {
    printf("\n═══ Pointer-to-Pointer (Double Pointer) ═══\n");

    int x = 7;
    int *p = &x;
    int **pp = &p;   /* pointer to pointer to int */

    printf("  x   = %d\n", x);
    printf("  *p  = %d\n", *p);
    printf("  **pp= %d\n", **pp);   /* double dereference */

    **pp = 99;
    printf("  After **pp = 99: x = %d\n", x);

    /* Use case: modify a pointer from inside a function */
    /* (See swap_pointers below) */
}

/* ── void Pointer (generic pointer) ──────────────────────────────────────── */

void swap(void *a, void *b, size_t size) {
    /* Generic swap using void* — works for any type */
    unsigned char *pa = (unsigned char*)a;
    unsigned char *pb = (unsigned char*)b;
    unsigned char tmp;
    for (size_t i = 0; i < size; i++) {
        tmp = pa[i]; pa[i] = pb[i]; pb[i] = tmp;
    }
}

void void_pointer_demo(void) {
    printf("\n═══ void* (Generic Pointer) ═══\n");

    int a = 5, b = 10;
    printf("  Before: a=%d, b=%d\n", a, b);
    swap(&a, &b, sizeof(int));
    printf("  After:  a=%d, b=%d\n", a, b);

    double x = 3.14, y = 2.71;
    printf("  Before: x=%.2f, y=%.2f\n", x, y);
    swap(&x, &y, sizeof(double));
    printf("  After:  x=%.2f, y=%.2f\n", x, y);
}

/* ── Function Pointers ────────────────────────────────────────────────────── */

int add(int a, int b) { return a + b; }
int mul(int a, int b) { return a * b; }
int sub(int a, int b) { return a - b; }

void function_pointers(void) {
    printf("\n═══ Function Pointers ═══\n");

    /* Declare function pointer type: fn(int,int)->int */
    int (*op)(int, int);

    /* Array of function pointers — basis of vtable/dynamic dispatch */
    int (*ops[3])(int, int) = {add, mul, sub};
    const char *names[] = {"add", "mul", "sub"};

    for (int i = 0; i < 3; i++) {
        printf("  %s(10, 3) = %d\n", names[i], ops[i](10, 3));
    }

    /* Callback pattern: pass function as argument */
    /* (the basis of C's qsort, Java's Comparator, JS callbacks) */
}

/* ── Pointer Comparison & Null ────────────────────────────────────────────── */

void pointer_comparison(void) {
    printf("\n═══ Pointer Comparison & NULL ═══\n");

    int arr[5] = {1,2,3,4,5};
    int *start = arr, *end = arr + 5;

    /* Iterate using pointer comparison — common in C standard library */
    printf("  Array elements: ");
    for (int *p = start; p < end; p++) printf("%d ", *p);
    printf("\n");

    /* NULL pointer: pointer that points to nothing */
    int *null_ptr = NULL;
    printf("  NULL pointer = %p\n", (void*)null_ptr);
    if (null_ptr == NULL) printf("  NULL check: pointer is null — safe to not dereference\n");

    /* Dereferencing NULL → SIGSEGV (segfault) — DON'T do this:
       *null_ptr = 42;   CRASH: Segmentation fault */
}

/* ── Pointer vs Array ────────────────────────────────────────────────────── */

void pointer_vs_array(void) {
    printf("\n═══ Pointer vs Array ═══\n");

    int arr[4] = {1, 2, 3, 4};
    int *p = arr;

    printf("  sizeof(arr) = %zu bytes (entire array)\n", sizeof(arr));
    printf("  sizeof(p)   = %zu bytes (pointer size on this platform)\n", sizeof(p));
    printf("  arr[2] = %d, p[2] = %d, *(p+2) = %d\n", arr[2], p[2], *(p+2));
    /* All three access methods are identical after the array decays */

    /* Difference: arr++ is ILLEGAL (array name is not an lvalue),
       but p++ is legal: */
    p++;
    printf("  After p++: *p = %d (points to arr[1])\n", *p);
}

/* ── Const Pointer Variations ─────────────────────────────────────────────── */

void const_pointers(void) {
    printf("\n═══ const Pointer Variations ═══\n");

    int x = 10, y = 20;

    /* pointer to const: cannot modify the pointed-to value */
    const int *p1 = &x;
    /* *p1 = 99; — ERROR: assignment of read-only location */
    p1 = &y;            /* OK: can change which address p1 points to */
    printf("  const int *p1 (pointer to const): *p1 = %d\n", *p1);

    /* const pointer: cannot change where it points */
    int * const p2 = &x;
    *p2 = 99;           /* OK: can modify the pointed-to value */
    /* p2 = &y; — ERROR: assignment of read-only variable */
    printf("  int * const p2 (const pointer): x = %d\n", x);
    x = 10;             /* restore */

    /* const pointer to const: neither can change */
    const int * const p3 = &x;
    /* *p3 = 5; — ERROR */
    /* p3 = &y; — ERROR */
    printf("  const int * const p3: *p3 = %d (fully immutable)\n", *p3);
}

/* ── Pointer to struct ────────────────────────────────────────────────────── */

typedef struct {
    int   x, y;
    float distance;
} Point;

void struct_pointer(void) {
    printf("\n═══ Pointer to Struct & Arrow Operator ═══\n");

    Point p = {3, 4, 0.0f};
    Point *pp = &p;

    /* Two equivalent ways to access struct members through pointer */
    printf("  (*pp).x = %d  (dereference + dot)\n", (*pp).x);
    printf("  pp->y   = %d  (arrow operator — syntactic sugar for above)\n", pp->y);

    pp->distance = (float)sqrt(p.x*p.x + p.y*p.y);
    printf("  pp->distance = %.2f\n", pp->distance);
}

/* ── Pointer Sizes on Different Architectures ─────────────────────────────── */

void pointer_sizes(void) {
    printf("\n═══ Pointer Sizes ═══\n");
    printf("  sizeof(char*)     = %zu bytes\n", sizeof(char*));
    printf("  sizeof(int*)      = %zu bytes\n", sizeof(int*));
    printf("  sizeof(double*)   = %zu bytes\n", sizeof(double*));
    printf("  sizeof(void*)     = %zu bytes\n", sizeof(void*));
    printf("  (All pointer sizes are identical on a given platform)\n");
    printf("  (32-bit: 4 bytes, 64-bit: 8 bytes)\n");

    /* uintptr_t: integer wide enough to hold any pointer */
    int x = 42;
    uintptr_t addr = (uintptr_t)&x;
    printf("  Address of x as integer: 0x%016" PRIxPTR "\n", addr);
}

int main(void) {
    printf("╔══════════════════════════════════════════════════╗\n");
    printf("║  Concept Demo: Pointers & Memory Addressing (C)  ║\n");
    printf("╚══════════════════════════════════════════════════╝\n");

    basic_pointers();
    double_pointer();
    void_pointer_demo();
    function_pointers();
    pointer_comparison();
    pointer_vs_array();
    const_pointers();
    struct_pointer();
    pointer_sizes();

    return 0;
}
