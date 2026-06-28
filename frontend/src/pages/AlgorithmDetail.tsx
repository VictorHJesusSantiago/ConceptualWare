import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { algorithmApi } from '../services/api.js';
import { useAlgorithmStore, useAuthStore } from '../store/index.js';

/**
 * Concept #6  — Paradigma orientado a eventos, Reativo
 * Concept #18 — Async: useMutation, async execution, WebSocket for real-time steps
 * Concept #26 — Performance: useCallback, useRef (avoid re-renders)
 * Concept #5  — Visualização de algoritmos passo-a-passo
 */

// ── Array Visualizer ─────────────────────────────────────────────────────────

interface ArrayVisualizerProps {
  array: number[];
  comparing?: [number, number];
  swapping?: [number, number];
  sorted?: number[];
}

function ArrayVisualizer({ array, comparing, swapping, sorted }: ArrayVisualizerProps): React.ReactElement {
  const max = Math.max(...array, 1);

  return (
    <div className="flex items-end gap-1 h-48 bg-gray-900 rounded-lg p-4">
      {array.map((val, idx) => {
        const isComparing = comparing?.includes(idx);
        const isSwapping  = swapping?.includes(idx);
        const isSorted    = sorted?.includes(idx);

        const color = isSwapping  ? 'bg-red-500'    :
                      isComparing ? 'bg-yellow-400'  :
                      isSorted    ? 'bg-green-500'   :
                                    'bg-blue-500';

        return (
          <div key={idx} className="flex-1 flex flex-col items-center gap-1">
            <span className="text-xs text-gray-500">{val}</span>
            <div
              className={`w-full rounded-t transition-all duration-200 ${color}`}
              style={{ height: `${(val / max) * 140}px` }}
            />
          </div>
        );
      })}
    </div>
  );
}

// ── Complexity Chart ──────────────────────────────────────────────────────────

function ComplexityChart({ notation }: { notation: string }): React.ReactElement {
  const nValues = [10, 50, 100, 500, 1000];

  const compute = (n: number): number => {
    if (notation.includes('n²'))     return n * n;
    if (notation.includes('n log n')) return n * Math.log2(n);
    if (notation.includes('log n'))   return Math.log2(n);
    if (notation === 'O(1)')          return 1;
    return n;
  };

  const ops = nValues.map(n => ({ n, ops: compute(n) }));
  const max = Math.max(...ops.map(o => o.ops), 1);

  return (
    <div className="space-y-2">
      <p className="text-xs text-gray-400 font-mono">{notation} operations:</p>
      {ops.map(({ n, ops: o }) => (
        <div key={n} className="flex items-center gap-3">
          <span className="text-xs text-gray-500 w-12 text-right">n={n}</span>
          <div className="flex-1 bg-gray-800 rounded-full h-2">
            <div
              className="h-2 bg-blue-500 rounded-full transition-all"
              style={{ width: `${Math.min(100, (o / max) * 100)}%` }}
            />
          </div>
          <span className="text-xs text-gray-400 w-24 font-mono">
            {o < 1000 ? Math.round(o) : `${(o / 1000).toFixed(1)}K`} ops
          </span>
        </div>
      ))}
    </div>
  );
}

// ── Main Detail Page ──────────────────────────────────────────────────────────

export default function AlgorithmDetail(): React.ReactElement {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const {
    executionInput, setExecutionInput,
    executionResult, setExecutionResult,
    setIsExecuting, isExecuting,
    addToRecentlyViewed,
  } = useAlgorithmStore();

  const [inputText, setInputText] = useState(executionInput.join(', '));
  const [activeTab, setActiveTab] = useState<'visualizer' | 'code' | 'complexity' | 'analysis'>('visualizer');

  const { data: algorithm, isLoading } = useQuery({
    queryKey: ['algorithm', slug],
    queryFn: () => algorithmApi.getBySlug(slug!),
    enabled: !!slug,
    onSuccess: (algo) => addToRecentlyViewed(algo.slug),
  });

  const executeMutation = useMutation({
    mutationFn: ({ slug, input }: { slug: string; input: number[] }) =>
      algorithmApi.execute(slug, input),
    onMutate: () => setIsExecuting(true),
    onSuccess: (result) => {
      setExecutionResult({ output: [...result.output], durationNs: result.durationNs });
      setIsExecuting(false);
    },
    onError: () => setIsExecuting(false),
  });

  const parseInput = useCallback((text: string): number[] => {
    return text.split(/[,\s]+/)
      .map(s => parseInt(s.trim(), 10))
      .filter(n => !isNaN(n) && n >= 0 && n <= 999_999);
  }, []);

  const handleExecute = useCallback(() => {
    if (!slug || !isAuthenticated) {
      navigate('/login');
      return;
    }
    const input = parseInput(inputText);
    if (input.length < 2 || input.length > 10_000) return;
    setExecutionInput(input);
    executeMutation.mutate({ slug, input });
  }, [slug, isAuthenticated, inputText, parseInput, setExecutionInput, executeMutation, navigate]);

  const generateRandom = useCallback(() => {
    const arr = Array.from({ length: 20 }, () => Math.floor(Math.random() * 100));
    setInputText(arr.join(', '));
  }, []);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!algorithm) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center text-gray-400">
        Algorithm not found.
      </div>
    );
  }

  const currentArray = executionResult?.output ?? parseInput(inputText);

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      {/* Nav */}
      <nav className="px-6 py-4 border-b border-gray-800">
        <div className="max-w-7xl mx-auto flex items-center gap-3">
          <button onClick={() => navigate('/algorithms')} className="text-gray-400 hover:text-white">
            ← Algorithms
          </button>
          <span className="text-gray-700">/</span>
          <span className="text-white font-medium">{algorithm.name}</span>
        </div>
      </nav>

      <div className="max-w-7xl mx-auto px-6 py-6 grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Left: Info */}
        <div className="space-y-4">
          <div>
            <h1 className="text-3xl font-bold text-white mb-1">{algorithm.name}</h1>
            <div className="flex items-center gap-2 mb-3">
              <span className="text-xs bg-gray-800 px-2 py-1 rounded">{algorithm.category}</span>
              <span className="text-xs text-yellow-400 font-semibold">{algorithm.difficulty}</span>
            </div>
            <p className="text-gray-400 text-sm">{algorithm.description}</p>
          </div>

          {/* Complexity */}
          <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
            <h3 className="font-semibold mb-3 text-sm text-gray-300">Complexity</h3>
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-gray-400">Time</span>
                <code className="text-blue-400">{algorithm.timeComplexity.time}</code>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-gray-400">Space</span>
                <code className="text-green-400">{algorithm.spaceComplexity.time}</code>
              </div>
            </div>
          </div>

          {/* Tags */}
          {algorithm.tags.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {algorithm.tags.map(tag => (
                <span key={tag} className="text-xs bg-gray-800 text-gray-400 px-2 py-1 rounded">
                  {tag}
                </span>
              ))}
            </div>
          )}

          {/* Stats */}
          <div className="flex gap-4 text-sm text-gray-500">
            <span>👁 {algorithm.viewCount.toLocaleString()}</span>
            <span>❤ {algorithm.likeCount.toLocaleString()}</span>
            <span>⭐ {algorithm.averageRating.toFixed(1)}</span>
          </div>
        </div>

        {/* Right: Interactive */}
        <div className="lg:col-span-2 space-y-4">
          {/* Tabs */}
          <div className="flex gap-1 bg-gray-900 rounded-lg p-1 w-fit">
            {(['visualizer', 'code', 'complexity', 'analysis'] as const).map(tab => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`px-3 py-1.5 text-sm rounded-md transition-colors capitalize ${
                  activeTab === tab
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-400 hover:text-white'
                }`}
              >
                {tab}
              </button>
            ))}
          </div>

          {/* Visualizer Tab */}
          {activeTab === 'visualizer' && (
            <div className="space-y-4">
              <ArrayVisualizer array={currentArray.slice(0, 50)} />

              {/* Input */}
              <div className="flex gap-2">
                <input
                  type="text"
                  value={inputText}
                  onChange={e => setInputText(e.target.value)}
                  placeholder="Enter numbers separated by commas"
                  className="flex-1 bg-gray-900 border border-gray-700 rounded-lg px-3 py-2 text-sm font-mono text-white focus:outline-none focus:border-blue-500"
                />
                <button
                  onClick={generateRandom}
                  className="px-3 py-2 bg-gray-800 hover:bg-gray-700 rounded-lg text-sm transition-colors"
                >
                  Random
                </button>
                <button
                  onClick={handleExecute}
                  disabled={isExecuting}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 rounded-lg text-sm font-medium transition-colors"
                >
                  {isExecuting ? 'Running...' : '▶ Run'}
                </button>
              </div>

              {/* Result */}
              {executionResult && (
                <div className="bg-gray-900 border border-green-500/30 rounded-lg p-3">
                  <div className="flex justify-between text-xs text-gray-400 mb-2">
                    <span>Output ({executionResult.output.length} elements)</span>
                    <span>{(executionResult.durationNs / 1_000_000).toFixed(3)} ms</span>
                  </div>
                  <code className="text-green-400 text-xs font-mono break-all">
                    [{executionResult.output.slice(0, 20).join(', ')}{executionResult.output.length > 20 ? '...' : ''}]
                  </code>
                </div>
              )}
            </div>
          )}

          {/* Complexity Tab */}
          {activeTab === 'complexity' && (
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-5 space-y-6">
              <div>
                <h3 className="text-sm font-semibold text-gray-300 mb-3">Time Complexity Growth</h3>
                <ComplexityChart notation={algorithm.timeComplexity.time} />
              </div>
              <div>
                <h3 className="text-sm font-semibold text-gray-300 mb-3">Space Complexity Growth</h3>
                <ComplexityChart notation={algorithm.spaceComplexity.time} />
              </div>
            </div>
          )}

          {/* Analysis Tab */}
          {activeTab === 'analysis' && (
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
              <h3 className="font-semibold mb-4">Algorithm Analysis</h3>
              <dl className="space-y-3 text-sm">
                {[
                  ['Time (Best)',    algorithm.timeComplexity.description],
                  ['Time (Average)', algorithm.timeComplexity.time],
                  ['Space',          algorithm.spaceComplexity.time],
                  ['Category',       algorithm.category],
                  ['Difficulty',     algorithm.difficulty],
                ].map(([label, value]) => (
                  <div key={label} className="flex justify-between">
                    <dt className="text-gray-400">{label}</dt>
                    <dd className="text-white font-mono">{value}</dd>
                  </div>
                ))}
              </dl>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
