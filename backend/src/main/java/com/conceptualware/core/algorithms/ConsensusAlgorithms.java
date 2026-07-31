package com.conceptualware.core.algorithms;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ConsensusAlgorithms {

    public enum RaftState { FOLLOWER, CANDIDATE, LEADER }

    public static class RaftNode {
        final int id;
        private volatile RaftState state     = RaftState.FOLLOWER;
        private volatile int       currentTerm = 0;
        private volatile int       votedFor    = -1;
        private final List<String> log         = new ArrayList<>();
        private volatile int       commitIndex = -1;
        private volatile int       leaderFor   = -1;

        private final List<RaftNode> peers;
        private final AtomicInteger  voteCount = new AtomicInteger(0);

        public RaftNode(int id, List<RaftNode> peers) {
            this.id    = id;
            this.peers = peers;
        }

        public synchronized boolean startElection() {
            currentTerm++;
            state    = RaftState.CANDIDATE;
            votedFor = id;
            voteCount.set(1);

            int term = currentTerm;
            int logSize = log.size();

            for (RaftNode peer : peers) {
                if (peer.id != id) {
                    boolean granted = peer.requestVote(term, id, logSize);
                    if (granted) voteCount.incrementAndGet();
                }
            }

            int quorum = (peers.size() + 1) / 2 + 1;
            if (voteCount.get() >= quorum) {
                state     = RaftState.LEADER;
                leaderFor = currentTerm;
                return true;
            }

            state = RaftState.FOLLOWER;
            return false;
        }

        public synchronized boolean requestVote(int term, int candidateId, int candidateLogSize) {
            if (term < currentTerm) return false;

            if (term > currentTerm) {
                currentTerm = term;
                state       = RaftState.FOLLOWER;
                votedFor    = -1;
            }

            boolean canVote = (votedFor == -1 || votedFor == candidateId)
                           && candidateLogSize >= log.size();
            if (canVote) votedFor = candidateId;
            return canVote;
        }

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

        public synchronized boolean replicateCommand(String command) {
            if (state != RaftState.LEADER) return false;

            log.add(command);
            int successCount = 1;

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

    public static List<RaftNode> createRaftCluster(int n) {
        List<RaftNode> nodes = new ArrayList<>();
        List<RaftNode> ref   = new ArrayList<>();
        for (int i = 0; i < n; i++) nodes.add(new RaftNode(i, ref));
        ref.addAll(nodes);
        return nodes;
    }

    public static class PaxosAcceptor {
        final int id;
        private int    promisedN  = -1;
        private int    acceptedN  = -1;
        private String acceptedValue;

        public PaxosAcceptor(int id) { this.id = id; }

        public synchronized Optional<PaxosPromise> prepare(int proposalN) {
            if (proposalN <= promisedN) return Optional.empty();
            promisedN = proposalN;
            return Optional.of(new PaxosPromise(id, proposalN, acceptedN, acceptedValue));
        }

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

        public Optional<String> propose(String proposedValue) {
            int n = generateProposalNumber();
            int quorum = acceptors.size() / 2 + 1;

            List<PaxosPromise> promises = new ArrayList<>();
            for (PaxosAcceptor acceptor : acceptors) {
                acceptor.prepare(n).ifPresent(promises::add);
            }

            if (promises.size() < quorum) return Optional.empty();

            String value = promises.stream()
                .filter(p -> p.prevAcceptedN() >= 0)
                .max(Comparator.comparingInt(PaxosPromise::prevAcceptedN))
                .map(PaxosPromise::prevAcceptedValue)
                .orElse(proposedValue);

            int accepted = 0;
            for (PaxosAcceptor acceptor : acceptors) {
                if (acceptor.accept(n, value)) accepted++;
            }

            if (accepted >= quorum) return Optional.of(value);
            return Optional.empty();
        }

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
