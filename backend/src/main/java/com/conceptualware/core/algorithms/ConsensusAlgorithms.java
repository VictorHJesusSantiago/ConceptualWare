package com.conceptualware.core.algorithms;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Concept #5 — Consensus Algorithms (Algoritmos de Consenso Distribuído):
 *
 *   RAFT — leader-based consensus protocol (Ongaro & Ousterhout, 2014).
 *     Designed for understandability. Key concepts:
 *       - Leader election: any node can become leader if it doesn't hear from current leader.
 *       - Log replication: all writes go through leader, replicated to majority before commit.
 *       - Terms: logical clock for leader validity.
 *       - Quorum: (n/2)+1 nodes must acknowledge before commit.
 *
 *   PAXOS — consensus protocol (Lamport, 1989).
 *     Three roles: Proposers, Acceptors, Learners.
 *     Two phases:
 *       Phase 1a (Prepare): proposer sends prepare(n) to acceptors
 *       Phase 1b (Promise): acceptors promise not to accept proposals < n
 *       Phase 2a (Accept): proposer sends accept(n, value)
 *       Phase 2b (Accepted): acceptors accept if still promised to n
 *
 *   Both algorithms tolerate f failures with 2f+1 nodes (Raft: n≥3 for 1 failure).
 *
 * Concept #5  — Distributed systems theory
 * Concept #17 — Concurrency: atomic operations, thread coordination
 */
public class ConsensusAlgorithms {

    // ── RAFT Simulation ───────────────────────────────────────────────────────

    public enum RaftState { FOLLOWER, CANDIDATE, LEADER }

    public static class RaftNode {
        final int id;
        private volatile RaftState state     = RaftState.FOLLOWER;
        private volatile int       currentTerm = 0;
        private volatile int       votedFor    = -1;
        private final List<String> log         = new ArrayList<>();
        private volatile int       commitIndex = -1;
        private volatile int       leaderFor   = -1; // term this node became leader

        // Cluster topology
        private final List<RaftNode> peers;
        private final AtomicInteger  voteCount = new AtomicInteger(0);

        public RaftNode(int id, List<RaftNode> peers) {
            this.id    = id;
            this.peers = peers;
        }

        /** Start election: increment term, vote for self, request votes from peers. */
        public synchronized boolean startElection() {
            currentTerm++;
            state    = RaftState.CANDIDATE;
            votedFor = id;
            voteCount.set(1); // vote for self

            int term = currentTerm;
            int logSize = log.size();

            for (RaftNode peer : peers) {
                if (peer.id != id) {
                    boolean granted = peer.requestVote(term, id, logSize);
                    if (granted) voteCount.incrementAndGet();
                }
            }

            int quorum = (peers.size() + 1) / 2 + 1; // majority of cluster
            if (voteCount.get() >= quorum) {
                state     = RaftState.LEADER;
                leaderFor = currentTerm;
                return true; // won election
            }

            state = RaftState.FOLLOWER; // lost election
            return false;
        }

        /** Respond to vote request. */
        public synchronized boolean requestVote(int term, int candidateId, int candidateLogSize) {
            if (term < currentTerm) return false; // stale term

            if (term > currentTerm) {
                currentTerm = term;
                state       = RaftState.FOLLOWER;
                votedFor    = -1;
            }

            boolean canVote = (votedFor == -1 || votedFor == candidateId)
                           && candidateLogSize >= log.size(); // candidate log at least as up-to-date
            if (canVote) votedFor = candidateId;
            return canVote;
        }

        /**
         * AppendEntries RPC: log replication (also used as heartbeat with empty entries).
         * Returns true if successfully replicated.
         */
        public synchronized boolean appendEntries(int term, int leaderId, List<String> entries) {
            if (term < currentTerm) return false;

            if (term > currentTerm) {
                currentTerm = term;
                state       = RaftState.FOLLOWER;
                votedFor    = -1;
            }

            log.addAll(entries);
            return true;
        }

        /**
         * Leader sends a command: appends to own log, replicates to majority, then commits.
         * @return true if committed to majority quorum
         */
        public synchronized boolean replicateCommand(String command) {
            if (state != RaftState.LEADER) return false;

            log.add(command);
            int successCount = 1; // self

            for (RaftNode peer : peers) {
                if (peer.id != id) {
                    boolean ok = peer.appendEntries(currentTerm, id, List.of(command));
                    if (ok) successCount++;
                }
            }

            int quorum = (peers.size() + 1) / 2 + 1;
            if (successCount >= quorum) {
                commitIndex = log.size() - 1;
                return true;
            }
            return false;
        }

        public RaftState getState()    { return state; }
        public int       getTerm()     { return currentTerm; }
        public List<String> getLog()   { return Collections.unmodifiableList(log); }
        public int       getCommitIndex() { return commitIndex; }

        @Override public String toString() {
            return "Node[%d:%s term=%d log=%d commitIdx=%d]"
                .formatted(id, state, currentTerm, log.size(), commitIndex);
        }
    }

    /** Create a Raft cluster of n nodes, all cross-referencing each other. */
    public static List<RaftNode> createRaftCluster(int n) {
        List<RaftNode> nodes = new ArrayList<>();
        List<RaftNode> ref   = new ArrayList<>(); // shared reference list
        for (int i = 0; i < n; i++) nodes.add(new RaftNode(i, ref));
        ref.addAll(nodes);
        return nodes;
    }

    // ── PAXOS Simulation ──────────────────────────────────────────────────────

    public static class PaxosAcceptor {
        final int id;
        private int    promisedN  = -1;  // highest proposal number promised to
        private int    acceptedN  = -1;  // proposal number of accepted value
        private String acceptedValue;

        public PaxosAcceptor(int id) { this.id = id; }

        /**
         * Phase 1b — Promise: acceptor promises not to accept proposals < n.
         * Returns previously accepted value (if any) for proposer to use.
         */
        public synchronized Optional<PaxosPromise> prepare(int proposalN) {
            if (proposalN <= promisedN) return Optional.empty(); // reject stale
            promisedN = proposalN;
            return Optional.of(new PaxosPromise(id, proposalN, acceptedN, acceptedValue));
        }

        /**
         * Phase 2b — Accept: accept the proposal if still promised to it.
         */
        public synchronized boolean accept(int proposalN, String value) {
            if (proposalN < promisedN) return false;
            acceptedN     = proposalN;
            acceptedValue = value;
            return true;
        }

        public Optional<String> getAcceptedValue() { return Optional.ofNullable(acceptedValue); }
    }

    public record PaxosPromise(int acceptorId, int proposalN, int prevAcceptedN, String prevAcceptedValue) {}

    public static class PaxosProposer {
        final int    id;
        private final List<PaxosAcceptor> acceptors;
        private int  proposalCounter = 0;

        public PaxosProposer(int id, List<PaxosAcceptor> acceptors) {
            this.id        = id;
            this.acceptors = acceptors;
        }

        /**
         * Full Paxos round: Phase 1 (Prepare/Promise) + Phase 2 (Accept/Accepted).
         * @param proposedValue value this proposer wants to commit
         * @return committed value (may differ if another proposer already succeeded)
         */
        public Optional<String> propose(String proposedValue) {
            int n = generateProposalNumber();
            int quorum = acceptors.size() / 2 + 1;

            // Phase 1: Prepare
            List<PaxosPromise> promises = new ArrayList<>();
            for (PaxosAcceptor acceptor : acceptors) {
                acceptor.prepare(n).ifPresent(promises::add);
            }

            if (promises.size() < quorum) return Optional.empty(); // failed to get quorum

            // If any acceptor already accepted a value, use the highest-numbered one
            String value = promises.stream()
                .filter(p -> p.prevAcceptedN() >= 0)
                .max(Comparator.comparingInt(PaxosPromise::prevAcceptedN))
                .map(PaxosPromise::prevAcceptedValue)
                .orElse(proposedValue); // use our proposed value if none previously accepted

            // Phase 2: Accept
            int accepted = 0;
            for (PaxosAcceptor acceptor : acceptors) {
                if (acceptor.accept(n, value)) accepted++;
            }

            if (accepted >= quorum) return Optional.of(value);
            return Optional.empty();
        }

        // Proposal numbers must be unique and increasing across proposers
        // Convention: proposalN = (counter * numProposers) + proposerId
        private int generateProposalNumber() {
            return ++proposalCounter * 100 + id;
        }
    }

    public static class PaxosCluster {
        private final List<PaxosAcceptor> acceptors;
        private final List<PaxosProposer> proposers;

        public PaxosCluster(int numAcceptors, int numProposers) {
            acceptors = new ArrayList<>();
            proposers = new ArrayList<>();
            for (int i = 0; i < numAcceptors; i++) acceptors.add(new PaxosAcceptor(i));
            for (int i = 0; i < numProposers; i++) proposers.add(new PaxosProposer(i, acceptors));
        }

        public Optional<String> runConsensus(String value, int proposerIdx) {
            return proposers.get(proposerIdx).propose(value);
        }

        public List<Optional<String>> getAcceptedValues() {
            return acceptors.stream().map(PaxosAcceptor::getAcceptedValue).toList();
        }
    }

    // ── CAP Theorem explanation ───────────────────────────────────────────────

    /**
     * CAP Theorem: A distributed system can only guarantee 2 of:
     *   C — Consistency (all nodes see same data at same time)
     *   A — Availability (every request receives a response)
     *   P — Partition tolerance (system works despite network splits)
     *
     *   Raft: CP — favors consistency (rejects writes during partition)
     *   Paxos: CP — same
     *   Cassandra: AP — eventual consistency, always available
     *   ZooKeeper: CP — Raft-like consensus
     */
    public record CAPClassification(String system, boolean consistent, boolean available,
                                     boolean partitionTolerant, String notes) {
        public static CAPClassification[] all() {
            return new CAPClassification[]{
                new CAPClassification("Raft",      true, false, true, "Linearizable reads/writes, leader required"),
                new CAPClassification("Paxos",     true, false, true, "CP, complex multi-Paxos for multi-decree"),
                new CAPClassification("Cassandra", false, true, true, "Tunable consistency (ONE to ALL)"),
                new CAPClassification("ZooKeeper", true, false, true, "ZAB protocol (Zookeeper Atomic Broadcast)"),
                new CAPClassification("etcd",      true, false, true, "Uses Raft"),
                new CAPClassification("DynamoDB",  false, true, true, "Eventually consistent by default"),
            };
        }
    }
}
