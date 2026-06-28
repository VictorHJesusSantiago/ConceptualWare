/**
 * C Concept #17 — fork(), exec(), wait() and Unix Signals
 *
 * Demonstrates OS-level process management primitives that are
 * impossible to implement in Java/TypeScript:
 *
 *   fork()     — create a new process (clone of current)
 *   exec()     — replace current process image with a new program
 *   wait()     — parent waits for child to exit
 *   goto       — structured jump (legitimate use: error cleanup)
 *   Signals    — asynchronous notifications between processes
 *   pipe()     — inter-process communication via file descriptors
 *   kill()     — send signal to a process
 *   sigaction()— register signal handlers
 *
 * Build: gcc -std=c11 -Wall -Wextra -o 05_fork_exec_signals 05_fork_exec_signals.c
 *
 * NOTE: fork() is POSIX-only (Linux/macOS). Does not compile on Windows
 *       (use WSL or a POSIX container).
 */

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>       /* fork, exec, pipe, read, write */
#include <sys/wait.h>     /* wait, waitpid, WIFEXITED, WEXITSTATUS */
#include <sys/types.h>
#include <signal.h>       /* kill, sigaction, SIGTERM, SIGCHLD */
#include <errno.h>
#include <fcntl.h>

/* ── goto for cleanup pattern ─────────────────────────────────────────────── */
/**
 * Concept: goto for resource cleanup (legitimate C idiom)
 *
 * In C, when multiple resources are allocated and an error occurs partway,
 * goto cleanly jumps to the cleanup section without deeply nested if-else.
 *
 * Pattern (Linux kernel uses this extensively):
 *   if (allocate_A) goto cleanup_A;
 *   if (allocate_B) goto cleanup_B;
 *   ...use resources...
 *   cleanup_B: free B;
 *   cleanup_A: free A;
 *   return result;
 */
int demo_goto_cleanup(void) {
    printf("\n=== goto: Resource Cleanup Pattern ===\n");

    char *buf1 = NULL;
    char *buf2 = NULL;
    FILE *file  = NULL;
    int   result = -1;

    buf1 = malloc(256);
    if (!buf1) { perror("malloc buf1"); goto cleanup; }
    strcpy(buf1, "hello from buf1");

    buf2 = malloc(512);
    if (!buf2) { perror("malloc buf2"); goto cleanup; }
    strcpy(buf2, "hello from buf2");

    /* Simulate an error condition (comment out to let it succeed) */
    int simulate_error = 0;
    if (simulate_error) goto cleanup;

    printf("buf1: %s\n", buf1);
    printf("buf2: %s\n", buf2);
    result = 0;

    /*
     * SINGLE cleanup section — all resources freed in reverse allocation order.
     * No nested ifs needed. goto jumps here from any error point above.
     */
cleanup:
    if (file)  { fclose(file);  file = NULL;  }
    if (buf2)  { free(buf2);    buf2 = NULL;  }
    if (buf1)  { free(buf1);    buf1 = NULL;  }

    printf("cleanup completed (result=%d)\n", result);
    return result;
}

/* ── Signal handling ──────────────────────────────────────────────────────── */

static volatile sig_atomic_t g_received_signal = 0;
static volatile sig_atomic_t g_child_exited    = 0;

void signal_handler(int signum) {
    /* Signal handlers must be async-signal-safe:
     * - No malloc, printf, or other non-reentrant functions
     * - Use write() (async-signal-safe) instead of printf
     * - Use sig_atomic_t for shared state with main thread
     */
    g_received_signal = signum;

    const char *msg;
    if      (signum == SIGUSR1)  msg = "[signal] caught SIGUSR1\n";
    else if (signum == SIGTERM)  msg = "[signal] caught SIGTERM\n";
    else if (signum == SIGCHLD)  { g_child_exited = 1; return; }
    else                          msg = "[signal] caught unknown signal\n";

    write(STDOUT_FILENO, msg, strlen(msg));  /* async-signal-safe */
}

void demo_signals(void) {
    printf("\n=== Signals: sigaction, kill, SIGUSR1 ===\n");

    /* sigaction() — preferred over signal() (POSIX, reliable, no race) */
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART;    /* auto-restart interrupted syscalls */

    if (sigaction(SIGUSR1, &sa, NULL) == -1) { perror("sigaction"); return; }
    if (sigaction(SIGTERM, &sa, NULL) == -1) { perror("sigaction"); return; }
    if (sigaction(SIGCHLD, &sa, NULL) == -1) { perror("sigaction"); return; }

    /* Send SIGUSR1 to ourselves */
    printf("PID %d sending SIGUSR1 to itself\n", getpid());
    kill(getpid(), SIGUSR1);

    if (g_received_signal == SIGUSR1) {
        printf("Signal received and handled: SIGUSR1\n");
    }

    /* Signal mask — temporarily block SIGUSR1 */
    sigset_t mask, old_mask;
    sigemptyset(&mask);
    sigaddset(&mask, SIGUSR1);
    sigprocmask(SIG_BLOCK, &mask, &old_mask);   /* block SIGUSR1 */
    printf("SIGUSR1 blocked — signal will be PENDING until unblocked\n");
    kill(getpid(), SIGUSR1);   /* goes to pending set */
    g_received_signal = 0;

    sigprocmask(SIG_SETMASK, &old_mask, NULL);  /* unblock → delivers pending signal */
    if (g_received_signal == SIGUSR1) {
        printf("Pending signal delivered when unblocked\n");
    }
}

/* ── fork() ───────────────────────────────────────────────────────────────── */
/**
 * fork() — create a new process (child) as an exact copy of the parent.
 *
 * Returns:
 *   > 0 in parent (child's PID)
 *   = 0 in child
 *   < 0 on error
 *
 * After fork:
 *   - Child gets a COPY of parent's address space (copy-on-write pages)
 *   - File descriptors are inherited (shared)
 *   - Signal handlers reset to SIG_DFL in child if sigaction SA_RESETHAND
 *   - Child has its own PID; parent PID is child's PPID
 */
void demo_fork(void) {
    printf("\n=== fork(): Parent and Child Processes ===\n");

    pid_t pid = fork();

    if (pid < 0) {
        perror("fork");
        return;
    }

    if (pid == 0) {
        /* ── CHILD PROCESS ── */
        printf("[child]  PID=%d, PPID=%d — I am the child\n", getpid(), getppid());

        /* Child can do work independently */
        for (int i = 0; i < 3; i++) {
            printf("[child]  counting %d\n", i);
            usleep(50000);  /* 50ms */
        }
        printf("[child]  exiting with code 42\n");
        exit(42);  /* child exits here */

    } else {
        /* ── PARENT PROCESS ── */
        printf("[parent] PID=%d — forked child with PID=%d\n", getpid(), pid);

        /*
         * wait() / waitpid() — parent must reap child.
         * Without wait(), exited child becomes a ZOMBIE (entry stays in process table).
         * WIFEXITED(status) — true if child exited normally
         * WEXITSTATUS(status) — the exit code (only valid if WIFEXITED)
         */
        int status;
        pid_t waited = waitpid(pid, &status, 0);  /* block until child exits */

        if (waited == pid && WIFEXITED(status)) {
            printf("[parent] child exited with code %d\n", WEXITSTATUS(status));
        } else if (WIFSIGNALED(status)) {
            printf("[parent] child killed by signal %d\n", WTERMSIG(status));
        }
    }
}

/* ── pipe() — inter-process communication ─────────────────────────────────── */
/**
 * pipe() creates a unidirectional data channel:
 *   pipefd[0] — read end
 *   pipefd[1] — write end
 *
 * After fork, parent writes to pipefd[1], child reads from pipefd[0]
 * (or vice versa). Close unused ends to avoid deadlock.
 *
 * This is how shell pipes (cmd1 | cmd2) work at the OS level.
 */
void demo_pipe(void) {
    printf("\n=== pipe(): Inter-Process Communication ===\n");

    int pipefd[2];
    if (pipe(pipefd) == -1) { perror("pipe"); return; }

    pid_t pid = fork();

    if (pid < 0) { perror("fork"); return; }

    if (pid == 0) {
        /* CHILD — reader */
        close(pipefd[1]);   /* close unused write end */

        char buf[256];
        ssize_t n = read(pipefd[0], buf, sizeof(buf) - 1);
        if (n > 0) {
            buf[n] = '\0';
            printf("[child]  received via pipe: \"%s\"\n", buf);
        }
        close(pipefd[0]);
        exit(0);

    } else {
        /* PARENT — writer */
        close(pipefd[0]);   /* close unused read end */

        const char *msg = "hello from parent via pipe!";
        write(pipefd[1], msg, strlen(msg));
        close(pipefd[1]);   /* close write end — sends EOF to child */

        printf("[parent] wrote to pipe, waiting for child...\n");
        waitpid(pid, NULL, 0);
        printf("[parent] child finished\n");
    }
}

/* ── exec() — replace process image ──────────────────────────────────────── */
/**
 * exec family: replace the CALLING PROCESS with a new program.
 * The PID stays the same; code, data, stack are replaced.
 *
 * Variants:
 *   execl():  args as C varargs list (NULL terminated)
 *   execv():  args as array (argv[])
 *   execle(): args as list + explicit envp[]
 *   execve(): args as array + envp[] (the actual syscall, others are wrappers)
 *   execlp(): searches PATH for the program
 *   execvp(): like execv() but searches PATH
 *
 * Pattern: fork() + exec() = run a new program as a child process.
 * This is how shells spawn commands: fork a child, then exec the command in it.
 */
void demo_fork_exec(void) {
    printf("\n=== fork() + exec(): Running a New Program ===\n");

    pid_t pid = fork();

    if (pid < 0) { perror("fork"); return; }

    if (pid == 0) {
        /* CHILD: replace itself with 'echo' command */
        char *argv[] = {"echo", "[exec] Hello from exec'd process!", NULL};
        execvp("echo", argv);

        /* execvp only returns on error */
        perror("execvp");
        exit(1);

    } else {
        int status;
        waitpid(pid, &status, 0);
        printf("[parent] exec'd child exited: %d\n", WEXITSTATUS(status));
    }
}

/* ── Orphan and Zombie process demo ─────────────────────────────────────────  */
/**
 * Orphan: parent exits before child → child reparented to init (PID 1).
 * Zombie: child exits but parent hasn't wait()'d → stays in process table as Z.
 *
 * This demo shows zombie prevention via SIGCHLD handler.
 */
void demo_zombie_prevention(void) {
    printf("\n=== Zombie Prevention via SIGCHLD ===\n");

    /*
     * The SIGCHLD handler set in demo_signals() will be triggered.
     * For automatic zombie prevention, set SA_NOCLDWAIT or use
     * signal(SIGCHLD, SIG_IGN) — tells kernel to auto-reap children.
     *
     * Here we use waitpid(WNOHANG) to non-blocking reap in the SIGCHLD handler.
     */

    pid_t pid = fork();
    if (pid < 0) { perror("fork"); return; }

    if (pid == 0) {
        printf("[child]  exiting immediately (potential zombie without wait)\n");
        exit(0);
    } else {
        /*
         * Parent sleeps briefly — without waitpid, child is a zombie during this time.
         * With our SIGCHLD handler and waitpid we prevent this.
         */
        usleep(100000);   /* 100ms */
        int status;
        pid_t reaped = waitpid(pid, &status, WNOHANG);  /* non-blocking */
        if (reaped == pid) {
            printf("[parent] reaped child (no zombie): exit=%d\n", WEXITSTATUS(status));
        } else {
            /* SIGCHLD handler may have already reaped it */
            printf("[parent] child already reaped by SIGCHLD handler\n");
        }
    }
}

/* ── main ─────────────────────────────────────────────────────────────────── */

int main(void) {
    printf("══════════════════════════════════════════════════\n");
    printf(" C OS Concepts: fork, exec, pipe, signals, goto\n");
    printf("══════════════════════════════════════════════════\n");

    demo_goto_cleanup();
    demo_signals();
    demo_fork();
    demo_pipe();
    demo_fork_exec();
    demo_zombie_prevention();

    printf("\n✓ All OS-level concept demos completed.\n");
    return 0;
}
