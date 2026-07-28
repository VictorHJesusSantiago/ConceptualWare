import React from 'react';
import { useTheme } from '../context/ThemeContext.js';

export default function ThemeToggle(): React.ReactElement {
  const { theme, toggleTheme } = useTheme();

  return (
    <button
      onClick={toggleTheme}
      aria-label={`Alternar para tema ${theme === 'dark' ? 'claro' : 'escuro'}`}
      title={`Tema atual: ${theme === 'dark' ? 'escuro' : 'claro'}`}
      className="fixed bottom-4 right-4 z-50 w-11 h-11 rounded-full flex items-center justify-center
                 bg-[var(--surface-2)] text-[var(--fg)] border border-[var(--border)]
                 shadow-lg hover:opacity-80 transition-opacity"
    >
      {theme === 'dark' ? '☀️' : '🌙'}
    </button>
  );
}
