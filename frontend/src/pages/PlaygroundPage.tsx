import React, { useState } from 'react';
import axios from 'axios';
import GraphVisualizer, { GraphEdgeInput } from '../components/GraphVisualizer.js';
import DiffView from '../components/DiffView.js';

/**
 * Concept #6  — Playground interativo: editor de entrada + botão "rodar"
 *   disparando execução real contra o backend (via gateway).
 * Concept #5  — Visualização de algoritmos de grafo (Tarjan/Kosaraju SCC).
 * Concept #26 — Diff view de complexidade (naive vs. otimizado).
 */

const GATEWAY = (import.meta as any).env?.['VITE_API_BASE_URL'] ?? 'http://localhost:3001';

const DEFAULT_EDGES: GraphEdgeInput[] = [
  { from: 0, to: 1 }, { from: 1, to: 2 }, { from: 2, to: 0 },
  { from: 2, to: 3 }, { from: 3, to: 4 }, { from: 4, to: 5 }, { from: 5, to: 3 },
];

function PlaygroundCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--surface-1)] p-5">
      <h2 className="text-lg font-semibold text-[var(--fg)] mb-4">{title}</h2>
      {children}
    </div>
  );
}

export default function PlaygroundPage(): React.ReactElement {
  const [vertices, setVertices] = useState(6);
  const [edgesJson, setEdgesJson] = useState(JSON.stringify(DEFAULT_EDGES, null, 2));
  const [components, setComponents] = useState<number[][]>([]);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [zText, setZText] = useState('abracadabra');
  const [zPattern, setZPattern] = useState('abra');
  const [zMatches, setZMatches] = useState<number[] | null>(null);

  async function runScc() {
    setRunning(true);
    setError(null);
    try {
      const edges = JSON.parse(edgesJson) as GraphEdgeInput[];
      const response = await axios.post(`${GATEWAY}/api/v1/core-concepts/graph/scc/tarjan`, {
        vertices,
        directed: true,
        edges,
      });
      setComponents(response.data as number[][]);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao executar SCC');
    } finally {
      setRunning(false);
    }
  }

  async function runZSearch() {
    setRunning(true);
    setError(null);
    try {
      const response = await axios.post(`${GATEWAY}/api/v1/core-concepts/strings/z-search`, {
        text: zText,
        pattern: zPattern,
      });
      setZMatches(response.data as number[]);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao executar Z-search');
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="min-h-screen p-8 max-w-6xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-[var(--fg)]">Playground de Conceitos</h1>
      <p className="text-sm opacity-70 text-[var(--fg)]">
        Executa demos reais contra o backend (via gateway) — sandbox público, sem autenticação.
      </p>

      {error && (
        <div className="rounded-lg bg-red-500/10 border border-red-500/30 p-3 text-red-500 text-sm">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <PlaygroundCard title="Grafos — Componentes Fortemente Conexos (Tarjan)">
          <label className="block text-xs mb-1 text-[var(--fg)] opacity-70">Vértices</label>
          <input
            type="number"
            value={vertices}
            onChange={(e) => setVertices(Number(e.target.value))}
            className="w-full mb-3 rounded-md bg-[var(--surface-2)] border border-[var(--border)] px-3 py-2 text-sm text-[var(--fg)]"
          />
          <label className="block text-xs mb-1 text-[var(--fg)] opacity-70">Arestas (JSON)</label>
          <textarea
            value={edgesJson}
            onChange={(e) => setEdgesJson(e.target.value)}
            rows={6}
            className="w-full mb-3 rounded-md bg-[var(--surface-2)] border border-[var(--border)] px-3 py-2 font-mono text-xs text-[var(--fg)]"
          />
          <button
            onClick={runScc}
            disabled={running}
            className="mb-4 px-4 py-2 rounded-md bg-blue-600 text-white text-sm font-medium disabled:opacity-50"
          >
            {running ? 'Executando...' : 'Rodar SCC'}
          </button>
          <GraphVisualizer
            vertices={vertices}
            edges={(() => { try { return JSON.parse(edgesJson); } catch { return []; } })()}
            components={components}
          />
        </PlaygroundCard>

        <div className="space-y-6">
          <PlaygroundCard title="Strings — Z-Algorithm Search">
            <label className="block text-xs mb-1 text-[var(--fg)] opacity-70">Texto</label>
            <input
              value={zText}
              onChange={(e) => setZText(e.target.value)}
              className="w-full mb-3 rounded-md bg-[var(--surface-2)] border border-[var(--border)] px-3 py-2 text-sm text-[var(--fg)]"
            />
            <label className="block text-xs mb-1 text-[var(--fg)] opacity-70">Padrão</label>
            <input
              value={zPattern}
              onChange={(e) => setZPattern(e.target.value)}
              className="w-full mb-3 rounded-md bg-[var(--surface-2)] border border-[var(--border)] px-3 py-2 text-sm text-[var(--fg)]"
            />
            <button
              onClick={runZSearch}
              disabled={running}
              className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm font-medium disabled:opacity-50"
            >
              {running ? 'Executando...' : 'Buscar'}
            </button>
            {zMatches !== null && (
              <p className="mt-3 text-sm font-mono text-[var(--fg)]">
                Ocorrências em: [{zMatches.join(', ')}]
              </p>
            )}
          </PlaygroundCard>

          <DiffView
            label="Busca de padrão: força bruta vs. Z-algorithm"
            before={{ time: 'O(n·m)', space: 'O(1)', n: zText.length, operations: zText.length * zPattern.length }}
            after={{ time: 'O(n+m)', space: 'O(n+m)', n: zText.length, operations: zText.length + zPattern.length }}
          />
        </div>
      </div>
    </div>
  );
}
