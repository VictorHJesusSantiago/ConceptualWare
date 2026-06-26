/**
 * MongoDB initialization script — Concept #11 (Databases: DDL, DML, indexes, aggregation)
 * Runs on first container start via MONGO_INITDB scripts.
 */

// Switch to app database
db = db.getSiblingDB('conceptualware');

// ── Collections & Indexes ──────────────────────────────────────────────────────

db.createCollection('users');
db.users.createIndex({ email: 1 },    { unique: true, name: 'idx_users_email' });
db.users.createIndex({ username: 1 }, { unique: true, name: 'idx_users_username' });
db.users.createIndex({ totalPoints: -1 }, { name: 'idx_users_leaderboard' });
db.users.createIndex({ createdAt: -1 },   { name: 'idx_users_created' });

db.createCollection('algorithms');
db.algorithms.createIndex({ slug: 1 },       { unique: true, name: 'idx_algorithms_slug' });
db.algorithms.createIndex({ category: 1 },   { name: 'idx_algorithms_category' });
db.algorithms.createIndex({ difficulty: 1 }, { name: 'idx_algorithms_difficulty' });
db.algorithms.createIndex({ tags: 1 },       { name: 'idx_algorithms_tags' });
db.algorithms.createIndex(
  { name: 'text', description: 'text', tags: 'text' },
  { name: 'idx_algorithms_fulltext', weights: { name: 10, tags: 5, description: 1 } }
);

db.createCollection('challenges');
db.challenges.createIndex({ slug: 1 },       { unique: true, name: 'idx_challenges_slug' });
db.challenges.createIndex({ difficulty: 1 }, { name: 'idx_challenges_difficulty' });
db.challenges.createIndex({ category: 1 },   { name: 'idx_challenges_category' });

// ── Seed: Algorithm documents ─────────────────────────────────────────────────

const now = new Date();

db.algorithms.insertMany([
  {
    _id: ObjectId(),
    slug: 'bubble-sort',
    name: 'Bubble Sort',
    description: 'A simple comparison-based sorting algorithm that repeatedly swaps adjacent elements if they are in the wrong order. Best case O(n) when already sorted (adaptive). Demonstrates algorithm analysis fundamentals.',
    category: 'SORTING',
    difficulty: 'EASY',
    timeComplexity: { time: 'O(n²)', space: 'O(1)', description: 'O(n) best case when already sorted' },
    spaceComplexity: { time: 'O(1)', space: 'O(1)', description: 'In-place sorting, constant extra space' },
    implementations: [
      { language: 'JAVA', code: 'for(int i=0;i<n-1;i++) for(int j=0;j<n-i-1;j++) if(arr[j]>arr[j+1]) swap(arr,j,j+1);', isOptimized: false },
      { language: 'TYPESCRIPT', code: 'function bubbleSort(arr: number[]) { for(let i=0;i<arr.length-1;i++) for(let j=0;j<arr.length-i-1;j++) if(arr[j]>arr[j+1]) [arr[j],arr[j+1]]=[arr[j+1],arr[j]]; return arr; }', isOptimized: false }
    ],
    tags: ['sorting', 'comparison', 'stable', 'in-place', 'adaptive'],
    viewCount: 0, likeCount: 0, totalRating: 0, ratingCount: 0, averageRating: 0.0,
    isPublished: true, createdAt: now, updatedAt: now
  },
  {
    _id: ObjectId(),
    slug: 'merge-sort',
    name: 'Merge Sort',
    description: 'A divide-and-conquer sorting algorithm that splits the array in half, recursively sorts each half, then merges them. Guarantees O(n log n) in all cases. Stable sort — equal elements preserve relative order.',
    category: 'SORTING',
    difficulty: 'MEDIUM',
    timeComplexity: { time: 'O(n log n)', space: 'O(n log n)', description: 'O(n log n) in all cases — best, average, and worst' },
    spaceComplexity: { time: 'O(n)', space: 'O(n)', description: 'Requires O(n) auxiliary space for merging' },
    implementations: [
      { language: 'JAVA', code: 'public static int[] mergeSort(int[] arr) { if(arr.length<=1) return arr; int mid=arr.length/2; return merge(mergeSort(Arrays.copyOfRange(arr,0,mid)),mergeSort(Arrays.copyOfRange(arr,mid,arr.length))); }', isOptimized: true }
    ],
    tags: ['sorting', 'divide-and-conquer', 'stable', 'recursive', 'guaranteed'],
    viewCount: 0, likeCount: 0, totalRating: 0, ratingCount: 0, averageRating: 0.0,
    isPublished: true, createdAt: now, updatedAt: now
  },
  {
    _id: ObjectId(),
    slug: 'quick-sort',
    name: 'Quick Sort',
    description: 'Divide-and-conquer algorithm that selects a pivot and partitions the array around it. Average O(n log n), worst-case O(n²) with bad pivot selection. Randomized variant reduces worst-case probability. In-place and cache-friendly.',
    category: 'SORTING',
    difficulty: 'MEDIUM',
    timeComplexity: { time: 'O(n log n)', space: 'O(n log n)', description: 'O(n²) worst case with bad pivot, O(n log n) average' },
    spaceComplexity: { time: 'O(log n)', space: 'O(log n)', description: 'O(log n) stack space for recursion' },
    implementations: [],
    tags: ['sorting', 'divide-and-conquer', 'in-place', 'unstable', 'cache-friendly'],
    viewCount: 0, likeCount: 0, totalRating: 0, ratingCount: 0, averageRating: 0.0,
    isPublished: true, createdAt: now, updatedAt: now
  },
  {
    _id: ObjectId(),
    slug: 'heap-sort',
    name: 'Heap Sort',
    description: 'Uses a max-heap to sort in place. First builds a heap in O(n) (heapify), then extracts maximum n times in O(log n) each. Guaranteed O(n log n) and in-place, but not stable.',
    category: 'SORTING',
    difficulty: 'MEDIUM',
    timeComplexity: { time: 'O(n log n)', space: 'O(n log n)', description: 'O(n) build heap + O(n log n) extraction' },
    spaceComplexity: { time: 'O(1)', space: 'O(1)', description: 'In-place using the input array as heap storage' },
    implementations: [],
    tags: ['sorting', 'heap', 'in-place', 'unstable', 'selection'],
    viewCount: 0, likeCount: 0, totalRating: 0, ratingCount: 0, averageRating: 0.0,
    isPublished: true, createdAt: now, updatedAt: now
  },
  {
    _id: ObjectId(),
    slug: 'dijkstra',
    name: "Dijkstra's Shortest Path",
    description: "Greedy algorithm for single-source shortest paths in weighted graphs with non-negative edges. Uses a min-heap priority queue for O((V+E) log V) time. Foundation for GPS navigation, network routing, and game AI pathfinding.",
    category: 'GRAPH',
    difficulty: 'HARD',
    timeComplexity: { time: 'O((V+E) log V)', space: 'O((V+E) log V)', description: 'With binary heap priority queue' },
    spaceComplexity: { time: 'O(V)', space: 'O(V)', description: 'Distance and parent arrays, plus priority queue' },
    implementations: [],
    tags: ['graph', 'shortest-path', 'greedy', 'priority-queue', 'weighted'],
    viewCount: 0, likeCount: 0, totalRating: 0, ratingCount: 0, averageRating: 0.0,
    isPublished: true, createdAt: now, updatedAt: now
  },
  {
    _id: ObjectId(),
    slug: 'bellman-ford',
    name: 'Bellman-Ford',
    description: 'Single-source shortest path algorithm that handles negative edge weights and detects negative cycles. Relaxes all edges V-1 times. O(VE) time — slower than Dijkstra but more general.',
    category: 'GRAPH',
    difficulty: 'HARD',
    timeComplexity: { time: 'O(V·E)', space: 'O(V·E)', description: 'V-1 iterations, each relaxing all E edges' },
    spaceComplexity: { time: 'O(V)', space: 'O(V)', description: 'Distance array only' },
    implementations: [],
    tags: ['graph', 'shortest-path', 'negative-weights', 'dynamic-programming'],
    viewCount: 0, likeCount: 0, totalRating: 0, ratingCount: 0, averageRating: 0.0,
    isPublished: true, createdAt: now, updatedAt: now
  },
  {
    _id: ObjectId(),
    slug: 'knapsack-01',
    name: '0/1 Knapsack',
    description: 'Classic dynamic programming problem: given items with weights and values, find the maximum value subset that fits in capacity W. Each item can be taken at most once. Optimal substructure + overlapping subproblems → DP.',
    category: 'DYNAMIC_PROGRAMMING',
    difficulty: 'MEDIUM',
    timeComplexity: { time: 'O(n·W)', space: 'O(n·W)', description: 'n items × W capacity states' },
    spaceComplexity: { time: 'O(n·W)', space: 'O(W)', description: 'O(W) with rolling array optimization' },
    implementations: [],
    tags: ['dynamic-programming', 'optimization', 'combinatorics', 'classic'],
    viewCount: 0, likeCount: 0, totalRating: 0, ratingCount: 0, averageRating: 0.0,
    isPublished: true, createdAt: now, updatedAt: now
  },
  {
    _id: ObjectId(),
    slug: 'kmp-search',
    name: 'KMP String Search',
    description: "Knuth-Morris-Pratt pattern matching uses the LPS (Longest Proper Prefix which is also Suffix) array to skip characters on mismatch. Achieves O(n+m) compared to naive O(nm). Critical for DNA sequence matching, text editors, and grep.",
    category: 'STRING',
    difficulty: 'HARD',
    timeComplexity: { time: 'O(n+m)', space: 'O(n+m)', description: 'n=text length, m=pattern length. LPS build O(m) + search O(n)' },
    spaceComplexity: { time: 'O(m)', space: 'O(m)', description: 'LPS array of size m' },
    implementations: [],
    tags: ['string', 'pattern-matching', 'lps', 'linear-time'],
    viewCount: 0, likeCount: 0, totalRating: 0, ratingCount: 0, averageRating: 0.0,
    isPublished: true, createdAt: now, updatedAt: now
  },
  {
    _id: ObjectId(),
    slug: 'huffman-coding',
    name: 'Huffman Coding',
    description: 'Greedy data compression algorithm that assigns variable-length prefix codes based on symbol frequency — frequent symbols get shorter codes. Used in JPEG, PNG, DEFLATE (gzip). Demonstrates greedy algorithms and binary trees.',
    category: 'COMPRESSION',
    difficulty: 'HARD',
    timeComplexity: { time: 'O(n log n)', space: 'O(n log n)', description: 'n=number of distinct symbols. Heap-based construction.' },
    spaceComplexity: { time: 'O(n)', space: 'O(n)', description: 'Huffman tree with 2n-1 nodes' },
    implementations: [],
    tags: ['compression', 'greedy', 'binary-tree', 'entropy', 'prefix-codes'],
    viewCount: 0, likeCount: 0, totalRating: 0, ratingCount: 0, averageRating: 0.0,
    isPublished: true, createdAt: now, updatedAt: now
  },
  {
    _id: ObjectId(),
    slug: 'a-star-search',
    name: 'A* Search',
    description: 'Informed search algorithm combining Dijkstra (g-cost: actual distance) with a heuristic (h-cost: estimated distance). f(n)=g(n)+h(n). With admissible heuristic, finds optimal path. Used in game AI, robotics, GPS navigation.',
    category: 'GRAPH',
    difficulty: 'EXPERT',
    timeComplexity: { time: 'O(b^d)', space: 'O(b^d)', description: 'b=branching factor, d=depth. Heuristic quality critical.' },
    spaceComplexity: { time: 'O(b^d)', space: 'O(b^d)', description: 'Stores all generated nodes in open/closed sets' },
    implementations: [],
    tags: ['graph', 'search', 'heuristic', 'pathfinding', 'game-ai'],
    viewCount: 0, likeCount: 0, totalRating: 0, ratingCount: 0, averageRating: 0.0,
    isPublished: true, createdAt: now, updatedAt: now
  },
]);

// ── Seed: Admin user ─────────────────────────────────────────────────────────
// Password: Admin@123 (BCrypt hash — change in production!)
db.users.insertOne({
  _id: ObjectId(),
  username: 'admin',
  email: 'admin@conceptualware.dev',
  passwordHash: '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewYpfQN.bGFAQk3y',
  roles: ['ADMIN', 'PREMIUM', 'USER'],
  status: 'ACTIVE',
  skillLevel: 'EXPERT',
  totalPoints: 10000,
  challengesSolved: 100,
  completedConcepts: [],
  currentStreak: 365,
  longestStreak: 365,
  refreshTokens: [],
  createdAt: now,
  updatedAt: now,
});

print('✓ ConceptualWare MongoDB initialized: indexes created, seed data inserted.');
