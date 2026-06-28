import React from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore, useAlgorithmStore } from '../store/index.js';
import { userApi, algorithmApi } from '../services/api.js';
import type { Algorithm } from '../types/index.js';

/**
 * Concept #6  — React: composition, hooks, conditional rendering
 * Concept #26 — Performance: react-query cache, memoized selectors
 * Concept #27 — Observability: display metrics (streak, points, skill level)
 */

function StatCard({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
      <p className="text-gray-400 text-sm mb-1">{label}</p>
      <p className="text-2xl font-bold text-white">{value}</p>
      {sub && <p className="text-xs text-gray-500 mt-1">{sub}</p>}
    </div>
  );
}

function AlgorithmRow({ algo }: { algo: Algorithm }) {
  const diffColor = algo.difficulty === 'EASY' ? 'text-green-400' :
                    algo.difficulty === 'MEDIUM' ? 'text-yellow-400' :
                    algo.difficulty === 'HARD' ? 'text-orange-400' : 'text-red-400';
  return (
    <Link
      to={`/algorithms/${algo.slug}`}
      className="flex items-center justify-between p-3 bg-gray-800/50 hover:bg-gray-800 rounded-lg transition-colors"
    >
      <div>
        <span className="text-sm font-medium text-white">{algo.name}</span>
        <span className="ml-2 text-xs text-gray-500 font-mono">{algo.category}</span>
      </div>
      <div className="flex items-center gap-3">
        <span className="text-xs font-mono text-gray-400">{algo.timeComplexity.time}</span>
        <span className={`text-xs font-semibold ${diffColor}`}>{algo.difficulty}</span>
      </div>
    </Link>
  );
}

export default function Dashboard(): React.ReactElement {
  const user = useAuthStore(s => s.user);
  const recentlyViewed = useAlgorithmStore(s => s.recentlyViewed);

  const { data: profile } = useQuery({
    queryKey: ['me'],
    queryFn: userApi.me,
    staleTime: 60_000,
  });

  const { data: featured } = useQuery({
    queryKey: ['algorithms', { page: 1, pageSize: 5 }],
    queryFn: () => algorithmApi.list({ page: 1, pageSize: 5 }),
    staleTime: 300_000,
  });

  const stats = [
    { label: 'Total Points',      value: profile?.totalPoints ?? 0,         sub: 'XP earned' },
    { label: 'Challenges Solved', value: profile?.challengesSolved ?? 0,    sub: 'problems' },
    { label: 'Current Streak',    value: `${profile?.currentStreak ?? 0}d`, sub: `best: ${profile?.longestStreak ?? 0}d` },
    { label: 'Skill Level',       value: profile?.skillLevel ?? '—',        sub: 'auto-updated' },
  ];

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      {/* Header */}
      <div className="border-b border-gray-800 px-6 py-5">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">
              Welcome back, {user?.username ?? profile?.username ?? '...'}
            </h1>
            <p className="text-gray-400 text-sm mt-0.5">Keep exploring concepts and sharpening your skills.</p>
          </div>
          <Link
            to="/algorithms"
            className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition-colors"
          >
            Explore Algorithms →
          </Link>
        </div>
      </div>

      <div className="max-w-6xl mx-auto px-6 py-6 space-y-6">
        {/* Stats */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {stats.map(s => <StatCard key={s.label} {...s} />)}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Featured Algorithms */}
          <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-white">Algorithms</h2>
              <Link to="/algorithms" className="text-xs text-blue-400 hover:text-blue-300">View all →</Link>
            </div>
            <div className="space-y-2">
              {featured?.items.map(algo => (
                <AlgorithmRow key={algo.id} algo={algo} />
              ))}
              {!featured && (
                <p className="text-gray-500 text-sm text-center py-4">Loading...</p>
              )}
            </div>
          </div>

          {/* Recently Viewed */}
          <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
            <h2 className="font-semibold text-white mb-4">Recently Viewed</h2>
            {recentlyViewed.length > 0 ? (
              <div className="space-y-2">
                {recentlyViewed.slice(0, 5).map(slug => (
                  <Link
                    key={slug}
                    to={`/algorithms/${slug}`}
                    className="flex items-center justify-between p-3 bg-gray-800/50 hover:bg-gray-800 rounded-lg transition-colors"
                  >
                    <span className="text-sm text-white font-mono">{slug}</span>
                    <span className="text-xs text-gray-500">→</span>
                  </Link>
                ))}
              </div>
            ) : (
              <div className="text-center py-8">
                <p className="text-gray-500 text-sm">No algorithms viewed yet.</p>
                <Link to="/algorithms" className="text-blue-400 text-sm mt-2 block">
                  Start exploring →
                </Link>
              </div>
            )}
          </div>
        </div>

        {/* Concept Categories quick access */}
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
          <h2 className="font-semibold text-white mb-4">Concept Categories</h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
            {[
              { label: 'Sorting',   count: 11, slug: 'SORTING' },
              { label: 'Graphs',    count: 8,  slug: 'GRAPH' },
              { label: 'DP',        count: 10, slug: 'DYNAMIC_PROGRAMMING' },
              { label: 'Strings',   count: 5,  slug: 'STRING' },
              { label: 'Math',      count: 15, slug: 'MATHEMATICAL' },
            ].map(cat => (
              <Link
                key={cat.slug}
                to={`/algorithms?category=${cat.slug}`}
                className="p-3 bg-gray-800 hover:bg-gray-700 rounded-lg transition-colors text-center"
              >
                <p className="text-white text-sm font-medium">{cat.label}</p>
                <p className="text-gray-500 text-xs mt-0.5">{cat.count} algorithms</p>
              </Link>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
