package com.conceptualware.core.os;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Concept #17 — Process Scheduling Algorithms (Escalonamento de Processos):
 *
 *   FIFO / FCFS — First Come, First Served:
 *     Processes run in arrival order until completion. No preemption.
 *     Problem: convoy effect — short jobs stuck behind long ones.
 *
 *   Round-Robin (RR):
 *     Each process gets a fixed time quantum, then preempted and queued again.
 *     Fair, used by most modern OS kernels. Quantum = 10-100ms in Linux.
 *
 *   SJF — Shortest Job First (non-preemptive):
 *     Runs the process with shortest burst time. Minimizes average waiting time.
 *     Problem: starvation of long processes, requires knowing burst time in advance.
 *
 *   SRTF — Shortest Remaining Time First (preemptive SJF):
 *     Preempts current process if a new arrival has shorter remaining time.
 *     Optimal average waiting time, but high context switch overhead.
 *
 *   Priority Scheduling:
 *     Each process has a priority. Highest priority runs first.
 *     Problem: starvation — low priority processes may never run (solved by aging).
 *
 *   Multilevel Queue:
 *     Separate queues for different process categories (foreground/background).
 *     Linux CFS (Completely Fair Scheduler) is based on this concept.
 *
 * Concept #17 — Operating systems, concurrent execution, time-sharing
 */
public class ProcessScheduler {

    // ── Process representation ────────────────────────────────────────────────

    public record Process(
        int    id,
        int    arrivalTime,
        int    burstTime,
        int    priority      // lower number = higher priority
    ) {
        @Override public String toString() {
            return "P%d(arr=%d,burst=%d,pri=%d)".formatted(id, arrivalTime, burstTime, priority);
        }
    }

    public record ScheduleResult(
        int    processId,
        int    startTime,
        int    finishTime,
        int    waitingTime,
        int    turnaroundTime,
        int    responseTime
    ) {}

    public record SchedulerStats(
        String algorithm,
        List<ScheduleResult> results,
        double avgWaitingTime,
        double avgTurnaroundTime,
        double cpuUtilization,
        double throughput
    ) {
        @Override public String toString() {
            return "%s — avg wait=%.2f avg turnaround=%.2f".formatted(algorithm, avgWaitingTime, avgTurnaroundTime);
        }
    }

    // ── FIFO / FCFS ───────────────────────────────────────────────────────────

    public static SchedulerStats fcfs(List<Process> processes) {
        List<Process> sorted = processes.stream()
            .sorted(Comparator.comparingInt(Process::arrivalTime))
            .collect(Collectors.toList());

        List<ScheduleResult> results = new ArrayList<>();
        int currentTime = 0;

        for (Process p : sorted) {
            currentTime = Math.max(currentTime, p.arrivalTime());
            int start     = currentTime;
            int finish    = currentTime + p.burstTime();
            int waiting   = start - p.arrivalTime();
            int turnaround = finish - p.arrivalTime();

            results.add(new ScheduleResult(p.id(), start, finish, waiting, turnaround, waiting));
            currentTime = finish;
        }

        return computeStats("FCFS", results, currentTime - sorted.get(0).arrivalTime());
    }

    // ── Round-Robin ───────────────────────────────────────────────────────────

    public static SchedulerStats roundRobin(List<Process> processes, int quantum) {
        // Mutable remaining burst times
        Map<Integer, Integer> remaining = new HashMap<>();
        Map<Integer, Integer> firstResponse = new HashMap<>();
        processes.forEach(p -> remaining.put(p.id(), p.burstTime()));

        List<Process> sorted = processes.stream()
            .sorted(Comparator.comparingInt(Process::arrivalTime))
            .collect(Collectors.toList());

        Queue<Process> ready = new LinkedList<>();
        List<ScheduleResult> results = new ArrayList<>();
        Map<Integer, Integer> finishTimes = new HashMap<>();

        int time = 0, idx = 0;
        if (!sorted.isEmpty() && sorted.get(0).arrivalTime() == 0)
            ready.add(sorted.get(idx++));

        while (!ready.isEmpty() || idx < sorted.size()) {
            if (ready.isEmpty()) {
                time = sorted.get(idx).arrivalTime();
                ready.add(sorted.get(idx++));
            }

            Process p = ready.poll();
            firstResponse.putIfAbsent(p.id(), time);

            int exec = Math.min(quantum, remaining.get(p.id()));
            time += exec;
            remaining.merge(p.id(), -exec, Integer::sum);

            // Admit newly arrived processes before re-queuing current
            while (idx < sorted.size() && sorted.get(idx).arrivalTime() <= time)
                ready.add(sorted.get(idx++));

            if (remaining.get(p.id()) > 0) {
                ready.add(p); // preempt: put back at end of queue
            } else {
                finishTimes.put(p.id(), time);
            }
        }

        processes.forEach(p -> {
            int finish    = finishTimes.get(p.id());
            int turnaround = finish - p.arrivalTime();
            int waiting   = turnaround - p.burstTime();
            results.add(new ScheduleResult(p.id(), firstResponse.get(p.id()),
                finish, waiting, turnaround, firstResponse.get(p.id()) - p.arrivalTime()));
        });

        int totalTime = finishTimes.values().stream().mapToInt(Integer::intValue).max().orElse(1)
                      - processes.stream().mapToInt(Process::arrivalTime).min().orElse(0);
        return computeStats("Round-Robin(q=" + quantum + ")", results, totalTime);
    }

    // ── SJF (non-preemptive) ──────────────────────────────────────────────────

    public static SchedulerStats sjf(List<Process> processes) {
        List<Process> remaining = new ArrayList<>(processes);
        List<ScheduleResult> results = new ArrayList<>();
        int time = 0;

        while (!remaining.isEmpty()) {
            // Among all processes that have arrived, pick shortest burst
            int finalTime = time;
            Optional<Process> shortest = remaining.stream()
                .filter(p -> p.arrivalTime() <= finalTime)
                .min(Comparator.comparingInt(Process::burstTime));

            if (shortest.isEmpty()) {
                time = remaining.stream().mapToInt(Process::arrivalTime).min().orElseThrow();
                continue;
            }

            Process p = shortest.get();
            remaining.remove(p);

            int start      = time;
            int finish     = start + p.burstTime();
            int waiting    = start - p.arrivalTime();
            int turnaround = finish - p.arrivalTime();

            results.add(new ScheduleResult(p.id(), start, finish, waiting, turnaround, waiting));
            time = finish;
        }

        int totalTime = results.stream().mapToInt(ScheduleResult::finishTime).max().orElse(1)
                      - processes.stream().mapToInt(Process::arrivalTime).min().orElse(0);
        return computeStats("SJF (non-preemptive)", results, totalTime);
    }

    // ── SRTF (preemptive SJF) ────────────────────────────────────────────────

    public static SchedulerStats srtf(List<Process> processes) {
        Map<Integer, Integer> remaining = new HashMap<>();
        Map<Integer, Integer> firstResponse = new HashMap<>();
        Map<Integer, Integer> finishTimes = new HashMap<>();
        processes.forEach(p -> remaining.put(p.id(), p.burstTime()));

        List<ScheduleResult> results = new ArrayList<>();
        int time = 0;
        int total = processes.stream().mapToInt(Process::burstTime).sum();

        for (int elapsed = 0; elapsed < total; elapsed++) {
            int t = time;
            Optional<Process> shortestReady = processes.stream()
                .filter(p -> p.arrivalTime() <= t && remaining.get(p.id()) > 0)
                .min(Comparator.comparingInt(p -> remaining.get(p.id())));

            if (shortestReady.isEmpty()) { time++; elapsed--; continue; }

            Process p = shortestReady.get();
            firstResponse.putIfAbsent(p.id(), time);
            remaining.merge(p.id(), -1, Integer::sum);
            time++;

            if (remaining.get(p.id()) == 0) finishTimes.put(p.id(), time);
        }

        processes.forEach(p -> {
            int finish     = finishTimes.get(p.id());
            int turnaround = finish - p.arrivalTime();
            int waiting    = turnaround - p.burstTime();
            results.add(new ScheduleResult(p.id(), firstResponse.get(p.id()),
                finish, waiting, turnaround, firstResponse.get(p.id()) - p.arrivalTime()));
        });

        int totalTime = time - processes.stream().mapToInt(Process::arrivalTime).min().orElse(0);
        return computeStats("SRTF (preemptive)", results, totalTime);
    }

    // ── Priority Scheduling (non-preemptive) ─────────────────────────────────

    public static SchedulerStats priorityScheduling(List<Process> processes) {
        List<Process> remaining = new ArrayList<>(processes);
        List<ScheduleResult> results = new ArrayList<>();
        int time = 0;

        while (!remaining.isEmpty()) {
            int finalTime = time;
            Optional<Process> highest = remaining.stream()
                .filter(p -> p.arrivalTime() <= finalTime)
                .min(Comparator.comparingInt(Process::priority)); // lower number = higher priority

            if (highest.isEmpty()) {
                time = remaining.stream().mapToInt(Process::arrivalTime).min().orElseThrow();
                continue;
            }

            Process p = highest.get();
            remaining.remove(p);

            int start      = time;
            int finish     = start + p.burstTime();
            int waiting    = start - p.arrivalTime();
            int turnaround = finish - p.arrivalTime();

            results.add(new ScheduleResult(p.id(), start, finish, waiting, turnaround, waiting));
            time = finish;
        }

        int totalTime = results.stream().mapToInt(ScheduleResult::finishTime).max().orElse(1)
                      - processes.stream().mapToInt(Process::arrivalTime).min().orElse(0);
        return computeStats("Priority (non-preemptive)", results, totalTime);
    }

    // ── Aging (prevent starvation) ────────────────────────────────────────────

    /**
     * Priority with aging: gradually increase priority of waiting processes.
     * Prevents indefinite starvation of low-priority processes.
     * Each unit of time waiting increases priority (decreases priority number) by 1.
     */
    public static SchedulerStats priorityWithAging(List<Process> processes, int agingRate) {
        Map<Integer, Integer> effectivePriority = new HashMap<>();
        processes.forEach(p -> effectivePriority.put(p.id(), p.priority()));

        List<Process> remaining = new ArrayList<>(processes);
        List<ScheduleResult> results = new ArrayList<>();
        int time = 0;

        while (!remaining.isEmpty()) {
            int finalTime = time;

            // Apply aging: reduce priority number for waiting processes
            remaining.forEach(p -> {
                if (p.arrivalTime() <= finalTime) {
                    effectivePriority.merge(p.id(), -agingRate, Integer::sum);
                }
            });

            Optional<Process> highest = remaining.stream()
                .filter(p -> p.arrivalTime() <= finalTime)
                .min(Comparator.comparingInt(p -> effectivePriority.get(p.id())));

            if (highest.isEmpty()) {
                time = remaining.stream().mapToInt(Process::arrivalTime).min().orElseThrow();
                continue;
            }

            Process p = highest.get();
            remaining.remove(p);

            int start      = time;
            int finish     = start + p.burstTime();
            int waiting    = start - p.arrivalTime();
            int turnaround = finish - p.arrivalTime();

            results.add(new ScheduleResult(p.id(), start, finish, waiting, turnaround, waiting));
            time = finish;
        }

        int totalTime = results.stream().mapToInt(ScheduleResult::finishTime).max().orElse(1)
                      - processes.stream().mapToInt(Process::arrivalTime).min().orElse(0);
        return computeStats("Priority + Aging", results, totalTime);
    }

    // ── Stats computation ─────────────────────────────────────────────────────

    private static SchedulerStats computeStats(String name, List<ScheduleResult> results, int totalTime) {
        double avgWait      = results.stream().mapToInt(ScheduleResult::waitingTime).average().orElse(0);
        double avgTurnaround = results.stream().mapToInt(ScheduleResult::turnaroundTime).average().orElse(0);
        int    busyTime     = results.stream().mapToInt(r -> r.finishTime() - r.startTime()).sum();
        double cpuUtil      = totalTime > 0 ? (double) busyTime / totalTime * 100 : 0;
        double throughput   = totalTime > 0 ? (double) results.size() / totalTime : 0;
        return new SchedulerStats(name, results, avgWait, avgTurnaround, cpuUtil, throughput);
    }

    // ── Gantt chart renderer ──────────────────────────────────────────────────

    public static String ganttChart(SchedulerStats stats) {
        StringBuilder sb = new StringBuilder();
        List<ScheduleResult> sorted = stats.results().stream()
            .sorted(Comparator.comparingInt(ScheduleResult::startTime))
            .toList();

        sb.append("|");
        sorted.forEach(r -> sb.append(" P%-3d|".formatted(r.processId())));
        sb.append("\n");

        sorted.forEach(r -> sb.append("%-5d".formatted(r.startTime())));
        sb.append("%-5d".formatted(sorted.get(sorted.size() - 1).finishTime()));
        return sb.toString();
    }
}
