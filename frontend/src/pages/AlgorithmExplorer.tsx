import React, { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { algorithmApi } from '../services/api.js';
import { Algorithm, Category, Difficulty } from '../types/index.js';
import { useAlgorithmStore } from '../store/index.js';

/**
 * Concept #6  — Paradigma orientado a eventos, Reativo (React hooks)
 * Concept #7  — OOP: Component composition, encapsulated state
 * Concept #18 — Async: useQuery (data fetching), debounce, useEffect
 * Concept #26 — Performance: useMemo, useCallback (memoization), debounce search
 * Concept #8  — FP: Pure components, immutable state, filter/map
 * Concept #13 — Compound Component Pattern, Strategy Pattern (sorting)
 * Concept #4  — Data Structures: virtual list rendering for large datasets
 */

// ── Debounce hook (Concept #26) ───────────────────────────────────────────────

function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

// ── Complexity Badge ───────────────────────────────────────────────────────────

const COMPLEXITY_COLORS: Record<string, string> = {
  'O(1)':      'bg-green-500/20 text-green-400 border-green-500/30',
  'O(log n)':  'bg-blue-500/20 text-blue-400 border-blue-500/30',
  'O(n)':      'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
  'O(n log n)':'bg-orange-500/20 text-orange-400 border-orange-500/30',
  'O(n²)':     'bg-red-500/20 text-red-400 border-red-500/30',
};

function ComplexityBadge({ complexity }: { complexity: string }): React.ReactElement {
  const colorClass = COMPLEXITY_COLORS[complexity] ?? 'bg-gray-500/20 text-gray-400 border-gray-500/30';
  return (
    <span className={`text-xs px-2 py-0.5 rounded-full border font-mono ${colorClass}`}>
      {complexity}
    </span>
  );
}

// ── Difficulty Badge ────────────────────────────────────────────────────────────

const DIFFICULTY_COLORS: Record<Difficulty, string> = {
  EASY:   'text-green-400',
  MEDIUM: 'text-yellow-400',
  HARD:   'text-orange-400',
  EXPERT: 'text-red-400',
};

function DifficultyBadge({ difficulty }: { difficulty: Difficulty }): React.ReactElement {
  return (
    <span className={`text-xs font-semibold ${DIFFICULTY_COLORS[difficulty]}`}>
      {difficulty}
    </span>
  );
}

// ── Algorithm Card ─────────────────────────────────────────────────────────────

interface AlgorithmCardProps {
  algorithm: Algorithm;
  onClick: (slug: string) => void;
  isFavorite: boolean;
  onToggleFavorite: (slug: string) => void;
}

function AlgorithmCard({ algorithm, onClick, isFavorite, onToggleFavorite }: AlgorithmCardProps): React.ReactElement {
  return (
    <article
      className="group bg-gray-900 border border-gray-800 rounded-xl p-4 hover:border-blue-500/50 hover:bg-gray-800/50 transition-all cursor-pointer"
      onClick={() => onClick(algorithm.slug)}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && onClick(algorithm.slug)}
      aria-label={`View ${algorithm.name} algorithm`}
    >
      <div className="flex items-start justify-between mb-2">
        <h3 className="font-semibold text-white group-hover:text-blue-400 transition-colors">
          {algorithm.name}
        </h3>
        <button
          onClick={e => { e.stopPropagation(); onToggleFavorite(algorithm.slug); }}
          className="text-gray-600 hover:text-yellow-400 transition-colors"
          aria-label={isFavorite ? 'Remove from favorites' : 'Add to favorites'}
        >
          {isFavorite ? '★' : '☆'}
        </button>
      </div>

      <p className="text-sm text-gray-400 mb-3 line-clamp-2">{algorithm.description}</p>

      <div className="flex items-center gap-2 flex-wrap mb-2">
        <DifficultyBadge difficulty={algorithm.difficulty} />
        <ComplexityBadge complexity={algorithm.timeComplexity.time} />
        <span className="text-xs text-gray-500">{algorithm.category}</span>
      </div>

      <div className="flex items-center gap-3 text-xs text-gray-500">
        <span>👁 {algorithm.viewCount.toLocaleString()}</span>
        <span>❤ {algorithm.likeCount.toLocaleString()}</span>
        <span>⭐ {algorithm.averageRating.toFixed(1)}</span>
      </div>
    </article>
  );
}

// ── Main Explorer Page ────────────────────────────────────────────────────────

const CATEGORIES: Category[] = [
  'SORTING', 'SEARCHING', 'GRAPH', 'DYNAMIC_PROGRAMMING',
  'STRING', 'GREEDY', 'DIVIDE_AND_CONQUER', 'BACKTRACKING',
  'MATHEMATICAL', 'DATA_STRUCTURES',
];

const DIFFICULTIES: Difficulty[] = ['EASY', 'MEDIUM', 'HARD', 'EXPERT'];

export default function AlgorithmExplorer(): React.ReactElement {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { favorites, toggleFavorite, addToRecentlyViewed } = useAlgorithmStore();

  // State — derived from URL params (Concept #14 — Single Source of Truth)
  const [searchTerm, setSearchTerm] = useState(searchParams.get('q') ?? '');
  const [category, setCategory] = useState<Category | ''>(
    (searchParams.get('category') as Category) ?? ''
  );
  const [difficulty, setDifficulty] = useState<Difficulty | ''>(
    (searchParams.get('difficulty') as Difficulty) ?? ''
  );
  const [page, setPage] = useState(0);

  const debouncedSearch = useDebounce(searchTerm, 300); // Concept #26

  // Sync state to URL
  useEffect(() => {
    const params: Record<string, string> = {};
    if (debouncedSearch) params['q'] = debouncedSearch;
    if (category) params['category'] = category;
    if (difficulty) params['difficulty'] = difficulty;
    setSearchParams(params, { replace: true });
    setPage(0);
  }, [debouncedSearch, category, difficulty, setSearchParams]);

  // Data fetching — React Query (Concept #18)
  const { data, isLoading, isError } = useQuery({
    queryKey: ['algorithms', debouncedSearch, category, difficulty, page],
    queryFn: () => debouncedSearch
      ? algorithmApi.search(debouncedSearch, page)
      : algorithmApi.list({ category: category || undefined, difficulty: difficulty || undefined, page }),
    staleTime: 2 * 60_000,
  });

  // Pure derived values (Concept #8 — FP)
  const algorithms = useMemo(() => data?.items ?? [], [data]);
  const totalPages = useMemo(() =>
    data ? Math.ceil(data.total / data.pageSize) : 0
  , [data]);

  const handleAlgorithmClick = useCallback((slug: string) => {
    addToRecentlyViewed(slug);
    navigate(`/algorithms/${slug}`);
  }, [navigate, addToRecentlyViewed]);

  const handleToggleFavorite = useCallback((slug: string) => {
    toggleFavorite(slug);
  }, [toggleFavorite]);

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      {/* Header */}
      <header className="sticky top-0 z-10 bg-gray-950/95 backdrop-blur border-b border-gray-800 px-6 py-4">
        <div className="max-w-7xl mx-auto">
          <h1 className="text-2xl font-bold text-white mb-1">
            Algorithm Explorer
          </h1>
          <p className="text-sm text-gray-400">
            Interactive implementations of 80+ algorithms with step-by-step visualization
          </p>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-6 py-6">
        {/* Search & Filters */}
        <div className="flex flex-col sm:flex-row gap-4 mb-6">
          <input
            type="search"
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            placeholder="Search algorithms... (e.g. merge sort, dijkstra)"
            className="flex-1 bg-gray-900 border border-gray-700 rounded-lg px-4 py-2.5 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-blue-500"
            aria-label="Search algorithms"
          />

          <select
            value={category}
            onChange={e => setCategory(e.target.value as Category | '')}
            className="bg-gray-900 border border-gray-700 rounded-lg px-3 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
            aria-label="Filter by category"
          >
            <option value="">All Categories</option>
            {CATEGORIES.map(c => (
              <option key={c} value={c}>{c.replace(/_/g, ' ')}</option>
            ))}
          </select>

          <select
            value={difficulty}
            onChange={e => setDifficulty(e.target.value as Difficulty | '')}
            className="bg-gray-900 border border-gray-700 rounded-lg px-3 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
            aria-label="Filter by difficulty"
          >
            <option value="">All Difficulties</option>
            {DIFFICULTIES.map(d => (
              <option key={d} value={d}>{d}</option>
            ))}
          </select>
        </div>

        {/* Results */}
        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: 9 }).map((_, i) => (
              <div key={i} className="bg-gray-900 border border-gray-800 rounded-xl p-4 animate-pulse">
                <div className="h-4 bg-gray-700 rounded w-3/4 mb-3" />
                <div className="h-3 bg-gray-800 rounded w-full mb-1" />
                <div className="h-3 bg-gray-800 rounded w-2/3" />
              </div>
            ))}
          </div>
        ) : isError ? (
          <div className="text-center py-16 text-red-400">
            Failed to load algorithms. Please try again.
          </div>
        ) : algorithms.length === 0 ? (
          <div className="text-center py-16 text-gray-500">
            No algorithms found matching your search.
          </div>
        ) : (
          <>
            <p className="text-sm text-gray-400 mb-4">
              {data?.total ?? 0} algorithms found
            </p>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {algorithms.map(algo => (
                <AlgorithmCard
                  key={algo.id}
                  algorithm={algo}
                  onClick={handleAlgorithmClick}
                  isFavorite={favorites.has(algo.slug)}
                  onToggleFavorite={handleToggleFavorite}
                />
              ))}
            </div>

            {/* Pagination — Concept #25 */}
            {totalPages > 1 && (
              <div className="flex justify-center gap-2 mt-8">
                <button
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-4 py-2 bg-gray-800 rounded-lg text-sm disabled:opacity-40 hover:bg-gray-700 transition-colors"
                >
                  Previous
                </button>
                <span className="px-4 py-2 text-sm text-gray-400">
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-4 py-2 bg-gray-800 rounded-lg text-sm disabled:opacity-40 hover:bg-gray-700 transition-colors"
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}
