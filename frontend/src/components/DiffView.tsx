import React from 'react';

/**
 * Concept #26 — Diff View: compara complexidade Big-O (ou qualquer par de
 * métricas) antes/depois de uma otimização, lado a lado.
 */

interface ComplexityDiffProps {
  label: string;
  before: { time: string; space: string; n?: number; operations?: number };
  after: { time: string; space: string; n?: number; operations?: number };
}

function improvementBadge(before: number | undefined, after: number | undefined): React.ReactElement | null {
  if (before === undefined || after === undefined || before === 0) return null;
  const change = ((before - after) / before) * 100;
  const improved = change > 0;
  return (
    <span className={`text-xs font-semibold ${improved ? 'text-green-500' : 'text-red-500'}`}>
      {improved ? '▼' : '▲'} {Math.abs(change).toFixed(1)}%
    </span>
  );
}

export default function DiffView({ label, before, after }: ComplexityDiffProps): React.ReactElement {
  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--surface-1)] p-4">
      <p className="text-sm font-medium text-[var(--fg)] mb-3">{label}</p>
      <div className="grid grid-cols-2 gap-4">
        <div className="rounded-lg bg-red-500/10 border border-red-500/30 p-3">
          <p className="text-xs uppercase tracking-wide text-red-500 mb-1">Antes</p>
          <p className="font-mono text-sm text-[var(--fg)]">time: {before.time}</p>
          <p className="font-mono text-sm text-[var(--fg)]">space: {before.space}</p>
          {before.operations !== undefined && (
            <p className="font-mono text-xs text-[var(--fg)] opacity-70">
              ~{before.operations.toLocaleString()} ops (n={before.n})
            </p>
          )}
        </div>
        <div className="rounded-lg bg-green-500/10 border border-green-500/30 p-3">
          <p className="text-xs uppercase tracking-wide text-green-500 mb-1">Depois</p>
          <p className="font-mono text-sm text-[var(--fg)]">time: {after.time}</p>
          <p className="font-mono text-sm text-[var(--fg)]">space: {after.space}</p>
          {after.operations !== undefined && (
            <p className="font-mono text-xs text-[var(--fg)] opacity-70">
              ~{after.operations.toLocaleString()} ops (n={after.n})
            </p>
          )}
        </div>
      </div>
      <div className="mt-2 flex justify-end">
        {improvementBadge(before.operations, after.operations)}
      </div>
    </div>
  );
}
