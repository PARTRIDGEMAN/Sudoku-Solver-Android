package com.partridgeman.sudokusolver.solver

import com.partridgeman.sudokusolver.model.SudokuBoard

sealed interface SolveResult {
    data object InvalidGivens : SolveResult
    data object Unsatisfiable : SolveResult
    data object MultipleSolutions : SolveResult
    data class Unique(val solution: SudokuBoard) : SolveResult
}

object SudokuSolver {
    /** Compatibility entry point: ambiguous boards never expose an arbitrary solution. */
    fun solve(board: SudokuBoard): SudokuBoard? = (analyze(board) as? SolveResult.Unique)?.solution

    fun analyze(board: SudokuBoard): SolveResult {
        if (!board.isValid()) return SolveResult.InvalidGivens
        val cells = board.cells.toIntArray()
        val rows = IntArray(9)
        val columns = IntArray(9)
        val boxes = IntArray(9)
        fun box(index: Int) = index / 27 * 3 + index % 9 / 3
        for (index in cells.indices) {
            if (cells[index] == 0) continue
            val bit = 1 shl cells[index]
            rows[index / 9] = rows[index / 9] or bit
            columns[index % 9] = columns[index % 9] or bit
            boxes[box(index)] = boxes[box(index)] or bit
        }
        var solutions = 0
        var first: SudokuBoard? = null
        fun search() {
            if (solutions == 2) return
            var target = -1
            var options = 0
            var bestCount = 10
            for (index in cells.indices) {
                if (cells[index] != 0) continue
                val mask = 0x3fe and (rows[index / 9] or columns[index % 9] or boxes[box(index)]).inv()
                val count = Integer.bitCount(mask)
                if (count == 0) return
                if (count < bestCount) {
                    target = index
                    options = mask
                    bestCount = count
                }
            }
            if (target == -1) {
                solutions++
                if (first == null) first = SudokuBoard(cells.toList())
                return
            }
            val row = target / 9
            val column = target % 9
            val boxIndex = box(target)
            while (options != 0 && solutions < 2) {
                // Ascending digits and row-major MRV ties make the search deterministic.
                val bit = options and -options
                options = options xor bit
                cells[target] = Integer.numberOfTrailingZeros(bit)
                rows[row] = rows[row] or bit
                columns[column] = columns[column] or bit
                boxes[boxIndex] = boxes[boxIndex] or bit
                search()
                cells[target] = 0
                rows[row] = rows[row] xor bit
                columns[column] = columns[column] xor bit
                boxes[boxIndex] = boxes[boxIndex] xor bit
            }
        }
        search()
        return when (solutions) {
            0 -> SolveResult.Unsatisfiable
            1 -> SolveResult.Unique(requireNotNull(first))
            else -> SolveResult.MultipleSolutions
        }
    }
}
