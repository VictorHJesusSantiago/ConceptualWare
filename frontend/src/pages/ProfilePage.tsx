import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../store/index.js';
import { userApi } from '../services/api.js';

/**
 * Concept #6  — React lifecycle, hooks
 * Concept #25 — REST API: user data
 * Concept #27 — Observability: display user metrics
 */

export default function ProfilePage(): React.ReactElement {
  const { user, logout } = useAuthStore();

  const { data: profile } = useQuery({
    queryKey: ['me'],
    queryFn: userApi.me,
    staleTime: 60_000,
  });

  const { data: progress } = useQuery({
    queryKey: ['progress'],
    queryFn: userApi.progress,
    staleTime: 120_000,
  });

  const info = profile ?? user;

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <div className="max-w-3xl mx-auto px-6 py-8 space-y-6">
        <h1 className="text-2xl font-bold">Profile</h1>

        {/* User card */}
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 flex items-start gap-5">
          <div className="w-16 h-16 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-2xl font-bold flex-shrink-0">
            {info?.username?.[0]?.toUpperCase() ?? '?'}
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="text-xl font-semibold">{info?.username ?? '...'}</h2>
            <p className="text-gray-400 text-sm">{info?.email}</p>
            <div className="flex gap-2 mt-2 flex-wrap">
              {(info?.roles ?? []).map(role => (
                <span key={role} className="text-xs bg-blue-500/20 text-blue-400 px-2 py-0.5 rounded border border-blue-500/30">
                  {role}
                </span>
              ))}
              <span className="text-xs bg-gray-800 text-gray-400 px-2 py-0.5 rounded">
                {profile?.skillLevel ?? '...'}
              </span>
            </div>
          </div>
        </div>

        {/* Stats grid */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {[
            { label: 'Total Points',      value: profile?.totalPoints ?? 0 },
            { label: 'Challenges Solved', value: profile?.challengesSolved ?? 0 },
            { label: 'Current Streak',    value: `${profile?.currentStreak ?? 0}d` },
            { label: 'Best Streak',       value: `${profile?.longestStreak ?? 0}d` },
          ].map(s => (
            <div key={s.label} className="bg-gray-900 border border-gray-800 rounded-xl p-4 text-center">
              <p className="text-2xl font-bold text-white">{s.value}</p>
              <p className="text-xs text-gray-400 mt-1">{s.label}</p>
            </div>
          ))}
        </div>

        {/* Progress */}
        {progress && (
          <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
            <h3 className="font-semibold mb-4">Concept Progress</h3>
            <div className="space-y-3">
              {Object.entries(progress as Record<string, number>).map(([category, pct]) => (
                <div key={category} className="space-y-1">
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-300">{category}</span>
                    <span className="text-gray-400">{pct}%</span>
                  </div>
                  <div className="h-1.5 bg-gray-800 rounded-full">
                    <div
                      className="h-1.5 bg-blue-500 rounded-full transition-all duration-500"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Account actions */}
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
          <h3 className="font-semibold mb-4 text-red-400">Danger Zone</h3>
          <button
            onClick={logout}
            className="px-4 py-2 bg-red-600/20 hover:bg-red-600/30 text-red-400 border border-red-500/30 rounded-lg text-sm transition-colors"
          >
            Sign out
          </button>
        </div>
      </div>
    </div>
  );
}
