package com.partridgeman.sudokusolver.model

class SudokuBoard(cells: List<Int>) {
    val cells: List<Int> = java.util.Collections.unmodifiableList(ArrayList(cells))

    override fun equals(other: Any?): Boolean = other is SudokuBoard && cells == other.cells
    override fun hashCode(): Int = cells.hashCode()
    override fun toString(): String = "SudokuBoard(cells=$cells)"
    init {
        require(cells.size == CELL_COUNT) { "A Sudoku board must contain exactly 81 cells." }
        require(cells.all { it in 0..9 }) { "Cells must contain values from 0 through 9." }
    }

    operator fun get(row: Int, column: Int): Int {
        require(row in 0 until SIZE && column in 0 until SIZE)
        return cells[row * SIZE + column]
    }

    fun withCell(row: Int, column: Int, value: Int): SudokuBoard {
        require(row in 0 until SIZE && column in 0 until SIZE)
        require(value in 0..9)
        val updated = cells.toMutableList()
        updated[row * SIZE + column] = value
        return SudokuBoard(updated)
    }

    fun isValid(): Boolean {
        for (index in 0 until SIZE) {
            if (hasDuplicate(nonZeroRow(index))) return false
            if (hasDuplicate(nonZeroColumn(index))) return false
        }

        for (boxRow in 0 until 3) {
            for (boxColumn in 0 until 3) {
                val values = buildList {
                    for (row in boxRow * 3 until boxRow * 3 + 3) {
                        for (column in boxColumn * 3 until boxColumn * 3 + 3) {
                            val value = this@SudokuBoard[row, column]
                            if (value != 0) add(value)
                        }
                    }
                }
                if (hasDuplicate(values)) return false
            }
        }
        return true
    }

    fun isSolved(): Boolean = cells.none { it == 0 } && isValid()

    private fun nonZeroRow(row: Int) = (0 until SIZE).map { column -> this[row, column] }.filter { it != 0 }
    private fun nonZeroColumn(column: Int) = (0 until SIZE).map { row -> this[row, column] }.filter { it != 0 }
    private fun hasDuplicate(values: List<Int>) = values.size != values.toSet().size

    companion object {
        const val SIZE = 9
        const val CELL_COUNT = SIZE * SIZE

        fun fromRows(rows: List<List<Int>>): SudokuBoard {
            require(rows.size == SIZE && rows.all { it.size == SIZE })
            return SudokuBoard(rows.flatten())
        }
    }
}
