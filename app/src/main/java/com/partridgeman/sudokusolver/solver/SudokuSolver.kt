package com.partridgeman.sudokusolver.solver

import com.partridgeman.sudokusolver.model.SudokuBoard

object SudokuSolver {
    fun solve(board: SudokuBoard): SudokuBoard? {
        if (!board.isValid()) return null
        val cells = board.cells.toIntArray()
        return if (solveMutable(cells)) SudokuBoard(cells.toList()) else null
    }

    private fun solveMutable(cells: IntArray): Boolean {
        val target = findBestEmptyCell(cells) ?: return true
        val used = usedValues(cells, target)

        for (candidate in 1..9) {
            if (!used[candidate]) {
                cells[target] = candidate
                if (solveMutable(cells)) return true
                cells[target] = 0
            }
        }
        return false
    }

    private fun findBestEmptyCell(cells: IntArray): Int? {
        var bestIndex: Int? = null
        var bestCandidateCount = 10

        for (index in cells.indices) {
            if (cells[index] != 0) continue
            val used = usedValues(cells, index)
            val candidateCount = (1..9).count { !used[it] }
            if (candidateCount == 0) return index
            if (candidateCount < bestCandidateCount) {
                bestIndex = index
                bestCandidateCount = candidateCount
                if (candidateCount == 1) break
            }
        }
        return bestIndex
    }

    private fun usedValues(cells: IntArray, index: Int): BooleanArray {
        val used = BooleanArray(10)
        val row = index / 9
        val column = index % 9

        for (i in 0 until 9) {
            used[cells[row * 9 + i]] = true
            used[cells[i * 9 + column]] = true
        }

        val boxRow = (row / 3) * 3
        val boxColumn = (column / 3) * 3
        for (r in boxRow until boxRow + 3) {
            for (c in boxColumn until boxColumn + 3) {
                used[cells[r * 9 + c]] = true
            }
        }
        return used
    }
}
