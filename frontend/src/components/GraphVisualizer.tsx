import React, { useMemo } from 'react';

/**
 * Concept #4/#5 — Visualização de Grafo: layout circular simples via SVG,
 * sem biblioteca de gráficos pesada (Concept #26 — bundle size).
 * Aceita destaque de componentes (ex.: SCC coloridos por grupo).
 */

export interface GraphEdgeInput {
  from: number;
  to: number;
  weight?: number;
}

interface GraphVisualizerProps {
  vertices: number;
  edges: GraphEdgeInput[];
  /** Cada sub-array é um grupo (ex.: componente fortemente conexo) — cores diferentes. */
  components?: number[][];
  size?: number;
}

const COLORS = ['#60a5fa', '#f97316', '#34d399', '#f472b6', '#a78bfa', '#facc15', '#22d3ee'];

export default function GraphVisualizer({
  vertices,
  edges,
  components = [],
  size = 420,
}: GraphVisualizerProps): React.ReactElement {
  const positions = useMemo(() => {
    const radius = size / 2 - 40;
    const center = size / 2;
    const pos: { x: number; y: number }[] = [];
    for (let i = 0; i < vertices; i++) {
      const angle = (2 * Math.PI * i) / Math.max(1, vertices) - Math.PI / 2;
      pos.push({ x: center + radius * Math.cos(angle), y: center + radius * Math.sin(angle) });
    }
    return pos;
  }, [vertices, size]);

  const colorOf = useMemo(() => {
    const map = new Map<number, string>();
    components.forEach((group, idx) => {
      group.forEach((v) => map.set(v, COLORS[idx % COLORS.length]!));
    });
    return (v: number) => map.get(v) ?? '#9ca3af';
  }, [components]);

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      role="img"
      aria-label="Visualização do grafo"
      className="bg-[var(--surface-1)] rounded-xl border border-[var(--border)]"
    >
      <defs>
        <marker id="arrow" markerWidth="8" markerHeight="8" refX="8" refY="4" orient="auto">
          <path d="M0,0 L8,4 L0,8 Z" fill="#6b7280" />
        </marker>
      </defs>

      {edges.map((edge, i) => {
        const from = positions[edge.from];
        const to = positions[edge.to];
        if (!from || !to) return null;
        return (
          <g key={`e-${i}`}>
            <line
              x1={from.x} y1={from.y} x2={to.x} y2={to.y}
              stroke="#6b7280" strokeWidth={1.5} markerEnd="url(#arrow)"
            />
            {edge.weight !== undefined && (
              <text
                x={(from.x + to.x) / 2}
                y={(from.y + to.y) / 2}
                fontSize={11}
                fill="#9ca3af"
                textAnchor="middle"
              >
                {edge.weight}
              </text>
            )}
          </g>
        );
      })}

      {positions.map((p, v) => (
        <g key={`v-${v}`}>
          <circle cx={p.x} cy={p.y} r={16} fill={colorOf(v)} stroke="var(--fg)" strokeWidth={1} />
          <text x={p.x} y={p.y + 4} fontSize={12} textAnchor="middle" fill="#0a0a0f" fontWeight={600}>
            {v}
          </text>
        </g>
      ))}
    </svg>
  );
}
