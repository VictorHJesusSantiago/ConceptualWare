import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../store/index.js';

/**
 * Concept #19 — Testing: TDD challenges, test cases, Given-When-Then
 * Concept #5  — Algorithms: categorized coding challenges
 * Concept #6  — React: interactive challenge UI
 */

type Difficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'EXPERT';

interface Challenge {
  id: string;
  title: string;
  description: string;
  difficulty: Difficulty;
  category: string;
  solved: boolean;
  acceptanceRate: number;
  tags: string[];
}

const MOCK_CHALLENGES: Challenge[] = [
  { id: '1', title: 'Two Sum',               difficulty: 'EASY',   category: 'DATA_STRUCTURES', solved: true,  acceptanceRate: 49, tags: ['hash-table', 'array'] },
  { id: '2', title: 'Valid Parentheses',      difficulty: 'EASY',   category: 'DATA_STRUCTURES', solved: true,  acceptanceRate: 64, tags: ['stack', 'string'] },
  { id: '3', title: 'Merge Intervals',        difficulty: 'MEDIUM', category: 'SORTING',         solved: false, acceptanceRate: 41, tags: ['sorting', 'greedy'] },
  { id: '4', title: 'LRU Cache',              difficulty: 'MEDIUM', category: 'DATA_STRUCTURES', solved: false, acceptanceRate: 38, tags: ['design', 'linked-list'] },
  { id: '5', title: 'Word Search',            difficulty: 'MEDIUM', category: 'GRAPH',           solved: false, acceptanceRate: 37, tags: ['backtracking', 'dfs'] },
  { id: '6', title: 'Coin Change',            difficulty: 'MEDIUM', category: 'DYNAMIC_PROGRAMMING', solved: false, acceptanceRate: 41, tags: ['dp', 'bfs'] },
  { id: '7', title: 'Trapping Rain Water',    difficulty: 'HARD',   category: 'ALGORITHMS',      solved: false, acceptanceRate: 57, tags: ['stack', 'dp', 'two-pointers'] },
  { id: '8', title: 'N-Queens',               difficulty: 'HARD',   category: 'BACKTRACKING',    solved: false, acceptanceRate: 64, tags: ['backtracking'] },
  { id: '9', title: 'Serialize/Deserialize BST', difficulty: 'HARD', category: 'DATA_STRUCTURES', solved: false, acceptanceRate: 55, tags: ['tree', 'design'] },
  { id: '10', title: 'Edit Distance',         difficulty: 'MEDIUM', category: 'DYNAMIC_PROGRAMMING', solved: false, acceptanceRate: 50, tags: ['dp', 'string'] },
];

const diffColor: Record<Difficulty, string> = {
  EASY:   'text-green-400',
  MEDIUM: 'text-yellow-400',
  HARD:   'text-orange-400',
  EXPERT: 'text-red-400',
};

const diffBg: Record<Difficulty, string> = {
  EASY:   'bg-green-500/10 border-green-500/30',
  MEDIUM: 'bg-yellow-500/10 border-yellow-500/30',
  HARD:   'bg-orange-500/10 border-orange-500/30',
  EXPERT: 'bg-red-500/10 border-red-500/30',
};

export default function ChallengesPage(): React.ReactElement {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const [filter, setFilter] = useState<Difficulty | 'ALL'>('ALL');
  const [search, setSearch] = useState('');

  const filtered = MOCK_CHALLENGES.filter(c => {
    if (filter !== 'ALL' && c.difficulty !== filter) return false;
    if (search && !c.title.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  const solved = MOCK_CHALLENGES.filter(c => c.solved).length;

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <div className="max-w-5xl mx-auto px-6 py-6 space-y-5">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold">Challenges</h1>
            <p className="text-gray-400 text-sm mt-0.5">{solved}/{MOCK_CHALLENGES.length} solved</p>
          </div>
        </div>

        {/* Progress bar */}
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
          <div className="flex justify-between text-sm mb-2">
            <span className="text-gray-400">Overall progress</span>
            <span className="text-white">{Math.round((solved / MOCK_CHALLENGES.length) * 100)}%</span>
          </div>
          <div className="h-2 bg-gray-800 rounded-full">
            <div
              className="h-2 bg-blue-500 rounded-full transition-all"
              style={{ width: `${(solved / MOCK_CHALLENGES.length) * 100}%` }}
            />
          </div>
        </div>

        {/* Filters */}
        <div className="flex flex-wrap gap-3 items-center">
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search challenges..."
            className="bg-gray-900 border border-gray-700 rounded-lg px-3 py-2 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 w-56"
          />
          <div className="flex gap-1 bg-gray-900 rounded-lg p-1">
            {(['ALL', 'EASY', 'MEDIUM', 'HARD', 'EXPERT'] as const).map(d => (
              <button
                key={d}
                onClick={() => setFilter(d)}
                className={`px-3 py-1 text-xs rounded-md font-medium transition-colors ${
                  filter === d
                    ? 'bg-gray-700 text-white'
                    : 'text-gray-500 hover:text-gray-300'
                }`}
              >
                {d}
              </button>
            ))}
          </div>
        </div>

        {/* Challenge list */}
        <div className="space-y-2">
          {filtered.map((ch, idx) => (
            <div
              key={ch.id}
              className="bg-gray-900 border border-gray-800 hover:border-gray-700 rounded-xl p-4 flex items-start gap-4 transition-colors cursor-pointer"
            >
              {/* Status */}
              <div className={`mt-0.5 w-5 h-5 rounded-full border-2 flex-shrink-0 flex items-center justify-center ${
                ch.solved
                  ? 'bg-green-500 border-green-500'
                  : 'border-gray-600'
              }`}>
                {ch.solved && <span className="text-white text-xs">✓</span>}
              </div>

              {/* Content */}
              <div className="flex-1 min-w-0">
                <div className="flex items-start justify-between gap-2">
                  <h3 className="text-sm font-medium text-white">
                    {idx + 1}. {ch.title}
                  </h3>
                  <span className={`text-xs font-semibold px-2 py-0.5 rounded border flex-shrink-0 ${diffBg[ch.difficulty]} ${diffColor[ch.difficulty]}`}>
                    {ch.difficulty}
                  </span>
                </div>
                <div className="flex items-center gap-3 mt-1.5 flex-wrap">
                  <span className="text-xs text-gray-500">{ch.category.replace('_', ' ')}</span>
                  <span className="text-xs text-gray-600">·</span>
                  <span className="text-xs text-gray-500">Acceptance: {ch.acceptanceRate}%</span>
                  <div className="flex gap-1.5 flex-wrap">
                    {ch.tags.map(tag => (
                      <span key={tag} className="text-xs bg-gray-800 text-gray-400 px-1.5 py-0.5 rounded">
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {!isAuthenticated && (
          <div className="text-center py-4">
            <p className="text-gray-400 text-sm">
              Sign in to track your progress and submit solutions.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
