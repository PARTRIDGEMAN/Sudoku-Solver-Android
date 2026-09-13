# Solver production invariants

Autofill must never rely on "the backtracker found something" as sufficient proof that a recognized puzzle is safe to enter.

The solver layer must distinguish:

- invalid givens (duplicate clue in a row/column/box),
- valid but unsatisfiable puzzle,
- exactly one solution,
- multiple solutions.

Only **exactly one solution** is eligible for later autofill.

## Required invariants

For every successful unique solve:

- all original non-zero clues are preserved,
- every row contains 1–9 exactly once,
- every column contains 1–9 exactly once,
- every 3×3 box contains 1–9 exactly once,
- the solver does not mutate the caller's input board,
- the result is deterministic for the same board,
- uniqueness checking stops once a second distinct solution is found rather than enumerating every solution.

## Tests

Add deterministic JVM tests for:

- known easy/medium/hard puzzles,
- already solved valid board,
- duplicate-given invalid board,
- valid-looking but unsatisfiable board,
- a known puzzle with multiple solutions,
- clue preservation and input immutability,
- repeated solves producing identical results,
- a small deterministic generated corpus or parameterized corpus large enough to exercise many blank patterns.

Performance tests should use generous, non-flaky bounds and exist only to catch catastrophic regressions. Correctness is more important than micro-benchmarks.
