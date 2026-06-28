import React, { useState } from 'react';

/**
 * Concept #23 — Metodologia: learning paths, concept dependencies
 * Concept #6  — React: rendering complex UI, composition
 * Concept #30 — AI/ML concepts displayed in a concept map
 *
 * Renders an interactive map of all 30 concept categories and their relationships.
 */

interface ConceptCategory {
  id: number;
  name: string;
  color: string;
  count: number;
  mastered: number;
  prerequisites: number[];
}

const CATEGORIES: ConceptCategory[] = [
  { id: 1,  name: 'Logic & Boolean Algebra',  color: '#3b82f6', count: 28, mastered: 0, prerequisites: [] },
  { id: 2,  name: 'Control Structures',        color: '#6366f1', count: 18, mastered: 0, prerequisites: [1] },
  { id: 3,  name: 'Variables & Types',         color: '#8b5cf6', count: 24, mastered: 0, prerequisites: [1] },
  { id: 4,  name: 'Data Structures',           color: '#ec4899', count: 30, mastered: 0, prerequisites: [2, 3] },
  { id: 5,  name: 'Algorithms',                color: '#f43f5e', count: 35, mastered: 0, prerequisites: [4] },
  { id: 6,  name: 'Programming Paradigms',     color: '#f97316', count: 18, mastered: 0, prerequisites: [2, 3] },
  { id: 7,  name: 'OOP',                       color: '#eab308', count: 22, mastered: 0, prerequisites: [3, 6] },
  { id: 8,  name: 'Functional Programming',    color: '#22c55e', count: 25, mastered: 0, prerequisites: [3, 6] },
  { id: 9,  name: 'Memory Management',         color: '#10b981', count: 20, mastered: 0, prerequisites: [3] },
  { id: 10, name: 'Compilation & Execution',   color: '#14b8a6', count: 15, mastered: 0, prerequisites: [3] },
  { id: 11, name: 'Databases',                 color: '#06b6d4', count: 30, mastered: 0, prerequisites: [4, 5] },
  { id: 12, name: 'Software Architecture',     color: '#0ea5e9', count: 28, mastered: 0, prerequisites: [7, 8] },
  { id: 13, name: 'Design Patterns',           color: '#3b82f6', count: 23, mastered: 0, prerequisites: [7, 12] },
  { id: 14, name: 'Engineering Principles',    color: '#6366f1', count: 20, mastered: 0, prerequisites: [7, 12] },
  { id: 15, name: 'Version Control',           color: '#8b5cf6', count: 15, mastered: 0, prerequisites: [] },
  { id: 16, name: 'Networks & Protocols',      color: '#a855f7', count: 25, mastered: 0, prerequisites: [10] },
  { id: 17, name: 'OS & Concurrency',          color: '#d946ef', count: 30, mastered: 0, prerequisites: [9, 10] },
  { id: 18, name: 'Async Programming',         color: '#ec4899', count: 20, mastered: 0, prerequisites: [17] },
  { id: 19, name: 'Software Testing',          color: '#f43f5e', count: 22, mastered: 0, prerequisites: [2, 6] },
  { id: 20, name: 'DevOps & CI/CD',            color: '#f97316', count: 25, mastered: 0, prerequisites: [15, 19] },
  { id: 21, name: 'Security',                  color: '#eab308', count: 30, mastered: 0, prerequisites: [16, 11] },
  { id: 22, name: 'Cloud Computing',           color: '#84cc16', count: 25, mastered: 0, prerequisites: [16, 20] },
  { id: 23, name: 'Agile & Methodologies',     color: '#22c55e', count: 18, mastered: 0, prerequisites: [] },
  { id: 24, name: 'System Analysis & Design',  color: '#10b981', count: 15, mastered: 0, prerequisites: [12, 23] },
  { id: 25, name: 'APIs & Integration',        color: '#14b8a6', count: 22, mastered: 0, prerequisites: [16, 12] },
  { id: 26, name: 'Performance Engineering',   color: '#06b6d4', count: 20, mastered: 0, prerequisites: [5, 17] },
  { id: 27, name: 'Observability',             color: '#0ea5e9', count: 15, mastered: 0, prerequisites: [20, 26] },
  { id: 28, name: 'Math for CS',               color: '#3b82f6', count: 25, mastered: 0, prerequisites: [1] },
  { id: 29, name: 'Clean Code & Refactoring',  color: '#6366f1', count: 22, mastered: 0, prerequisites: [14, 19] },
  { id: 30, name: 'AI & Machine Learning',     color: '#8b5cf6', count: 25, mastered: 0, prerequisites: [5, 8, 28] },
];

function CategoryCard({ cat, isSelected, onClick }: {
  cat: ConceptCategory;
  isSelected: boolean;
  onClick: () => void;
}) {
  const pct = cat.count > 0 ? Math.round((cat.mastered / cat.count) * 100) : 0;

  return (
    <button
      onClick={onClick}
      className={`text-left p-4 rounded-xl border transition-all ${
        isSelected
          ? 'border-white/30 bg-white/10 scale-[1.02]'
          : 'border-gray-800 bg-gray-900 hover:border-gray-700 hover:bg-gray-800'
      }`}
    >
      <div className="flex items-start justify-between gap-2 mb-2">
        <span
          className="w-2.5 h-2.5 rounded-full flex-shrink-0 mt-1"
          style={{ backgroundColor: cat.color }}
        />
        <span className="text-xs font-bold text-gray-500 ml-auto">#{cat.id}</span>
      </div>
      <p className="text-sm font-medium text-white leading-snug mb-2">{cat.name}</p>
      <div className="h-1 bg-gray-800 rounded-full mb-1">
        <div
          className="h-1 rounded-full transition-all"
          style={{ width: `${pct}%`, backgroundColor: cat.color }}
        />
      </div>
      <p className="text-xs text-gray-500">{cat.mastered}/{cat.count} concepts</p>
    </button>
  );
}

export default function ConceptMapPage(): React.ReactElement {
  const [selected, setSelected] = useState<ConceptCategory | null>(null);

  const prerequisites = selected
    ? CATEGORIES.filter(c => selected.prerequisites.includes(c.id))
    : [];

  const dependents = selected
    ? CATEGORIES.filter(c => c.prerequisites.includes(selected.id))
    : [];

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <div className="max-w-7xl mx-auto px-6 py-6">
        <div className="mb-6">
          <h1 className="text-2xl font-bold">Concept Map</h1>
          <p className="text-gray-400 text-sm mt-1">
            {CATEGORIES.length} categories · {CATEGORIES.reduce((s, c) => s + c.count, 0)} total concepts
          </p>
        </div>

        <div className="flex gap-6">
          {/* Grid */}
          <div className="flex-1 grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
            {CATEGORIES.map(cat => (
              <CategoryCard
                key={cat.id}
                cat={cat}
                isSelected={selected?.id === cat.id}
                onClick={() => setSelected(prev => prev?.id === cat.id ? null : cat)}
              />
            ))}
          </div>

          {/* Detail panel */}
          {selected && (
            <div className="w-72 flex-shrink-0">
              <div className="bg-gray-900 border border-gray-800 rounded-xl p-5 sticky top-6 space-y-4">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="w-3 h-3 rounded-full" style={{ backgroundColor: selected.color }} />
                    <span className="text-xs text-gray-500">Category #{selected.id}</span>
                  </div>
                  <h3 className="text-lg font-semibold text-white">{selected.name}</h3>
                  <p className="text-sm text-gray-400 mt-1">{selected.count} concepts</p>
                </div>

                {prerequisites.length > 0 && (
                  <div>
                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Prerequisites</p>
                    <div className="space-y-1">
                      {prerequisites.map(pre => (
                        <button
                          key={pre.id}
                          onClick={() => setSelected(pre)}
                          className="w-full text-left text-sm text-blue-400 hover:text-blue-300 flex items-center gap-2"
                        >
                          <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: pre.color }} />
                          {pre.name}
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {dependents.length > 0 && (
                  <div>
                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Unlocks</p>
                    <div className="space-y-1">
                      {dependents.map(dep => (
                        <button
                          key={dep.id}
                          onClick={() => setSelected(dep)}
                          className="w-full text-left text-sm text-green-400 hover:text-green-300 flex items-center gap-2"
                        >
                          <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: dep.color }} />
                          {dep.name}
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                <button
                  onClick={() => setSelected(null)}
                  className="w-full text-center text-xs text-gray-500 hover:text-gray-400 transition-colors"
                >
                  Dismiss
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
