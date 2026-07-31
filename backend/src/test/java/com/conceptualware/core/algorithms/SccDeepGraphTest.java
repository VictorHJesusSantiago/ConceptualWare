package com.conceptualware.core.algorithms;

import com.conceptualware.core.algorithms.graph.GraphAlgorithms;
import com.conceptualware.core.datastructures.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SCC — grafos profundos (regressão de StackOverflow)")
class SccDeepGraphTest {

    private static final int DEEP_CHAIN_SIZE = 100_000;

    private static Graph deepChain(int n) {
        Graph g = new Graph(n, true);
        for (int i = 0; i < n - 1; i++) g.addEdge(i, i + 1);
        return g;
    }

    private static Graph deepCycle(int n) {
        Graph g = deepChain(n);
        g.addEdge(n - 1, 0);
        return g;
    }

    @Test
    @DisplayName("Tarjan — cadeia profunda não estoura a pilha e cada vértice é um SCC")
    void tarjanHandlesDeepChain() {
        List<List<Integer>> sccs = GraphAlgorithms.tarjanSCC(deepChain(DEEP_CHAIN_SIZE));

        assertThat(sccs).hasSize(DEEP_CHAIN_SIZE);
        assertThat(sccs).allSatisfy(component -> assertThat(component).hasSize(1));
    }

    @Test
    @DisplayName("Kosaraju — cadeia profunda não estoura a pilha e cada vértice é um SCC")
    void kosarajuHandlesDeepChain() {
        List<List<Integer>> sccs = GraphAlgorithms.kosarajuSCC(deepChain(DEEP_CHAIN_SIZE));

        assertThat(sccs).hasSize(DEEP_CHAIN_SIZE);
        assertThat(sccs).allSatisfy(component -> assertThat(component).hasSize(1));
    }

    @Test
    @DisplayName("Tarjan — ciclo profundo é reconhecido como um único componente")
    void tarjanHandlesDeepCycle() {
        List<List<Integer>> sccs = GraphAlgorithms.tarjanSCC(deepCycle(DEEP_CHAIN_SIZE));

        assertThat(sccs).hasSize(1);
        assertThat(sccs.get(0)).hasSize(DEEP_CHAIN_SIZE);
    }

    @Test
    @DisplayName("Kosaraju — ciclo profundo é reconhecido como um único componente")
    void kosarajuHandlesDeepCycle() {
        List<List<Integer>> sccs = GraphAlgorithms.kosarajuSCC(deepCycle(DEEP_CHAIN_SIZE));

        assertThat(sccs).hasSize(1);
        assertThat(sccs.get(0)).hasSize(DEEP_CHAIN_SIZE);
    }

    @Test
    @DisplayName("Tarjan e Kosaraju concordam num grafo com múltiplos SCCs")
    void bothAlgorithmsAgree() {
        Graph g = new Graph(6, true);
        g.addEdge(0, 1); g.addEdge(1, 2); g.addEdge(2, 0);
        g.addEdge(2, 3);
        g.addEdge(3, 4); g.addEdge(4, 5); g.addEdge(5, 3);

        var tarjan   = GraphAlgorithms.tarjanSCC(g);
        var kosaraju = GraphAlgorithms.kosarajuSCC(g);

        assertThat(tarjan).hasSize(2);
        assertThat(kosaraju).hasSize(2);

        var tarjanSets   = tarjan.stream().map(java.util.HashSet::new).collect(java.util.stream.Collectors.toSet());
        var kosarajuSets = kosaraju.stream().map(java.util.HashSet::new).collect(java.util.stream.Collectors.toSet());
        assertThat(tarjanSets).isEqualTo(kosarajuSets);
    }
}
