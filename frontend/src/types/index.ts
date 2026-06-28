/**
 * Concept #3 — Tipos TypeScript do frontend
 * (mirrors gateway types with React-specific additions)
 */

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'EXPERT';
export type Category   = 'SORTING' | 'SEARCHING' | 'GRAPH' | 'DYNAMIC_PROGRAMMING' |
                         'STRING' | 'GREEDY' | 'DIVIDE_AND_CONQUER' | 'BACKTRACKING' |
                         'MATHEMATICAL' | 'DATA_STRUCTURES' | 'COMPRESSION' | 'CRYPTOGRAPHY';
export type Role       = 'USER' | 'PREMIUM' | 'ADMIN';
export type SkillLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';

export interface Complexity { time: string; space: string; description: string; }

export interface Algorithm {
  id: string;
  slug: string;
  name: string;
  description: string;
  category: Category;
  difficulty: Difficulty;
  timeComplexity: Complexity;
  spaceComplexity: Complexity;
  tags: string[];
  viewCount: number;
  likeCount: number;
  averageRating: number;
  createdAt: string;
  updatedAt: string;
}

export interface ExecutionResult {
  algorithmName: string;
  input: number[];
  output: number[];
  durationNs: number;
  timeComplexity: string;
  spaceComplexity: string;
}

export interface Paginated<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  hasNext: boolean;
  hasPrev: boolean;
}

export interface User {
  id: string;
  username: string;
  email: string;
  roles: Role[];
  skillLevel: SkillLevel;
  totalPoints: number;
  challengesSolved: number;
  currentStreak: number;
  longestStreak: number;
  createdAt: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  userId: string;
  username: string;
}

export interface JwtPayload {
  sub: string;
  email: string;
  roles: Role[];
  type: 'access' | 'refresh';
  exp: number;
  iat: number;
}
