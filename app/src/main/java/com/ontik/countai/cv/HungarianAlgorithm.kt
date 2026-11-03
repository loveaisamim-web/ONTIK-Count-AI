package com.ontik.countai.cv

/**
 * Exact O(N^3) Hungarian Algorithm (Kuhn-Munkres potential algorithm) for minimum-weight
 * bipartite matching between predicted Kalman tracks and current-frame grain detections.
 */
object HungarianAlgorithm {

    /**
     * Solves the rectangular assignment problem for `costMatrix` of shape `[numTracks][numDetections]`.
     * Returns an IntArray of length `numTracks` where `result[t]` is the assigned detection index `d`,
     * or `-1` if track `t` is unassigned or its assignment cost exceeds `maxGatingCost[t]`.
     */
    fun solveAssignment(
        costMatrix: Array<FloatArray>,
        maxGatingCost: FloatArray
    ): IntArray {
        val nRows = costMatrix.size
        if (nRows == 0) return IntArray(0)
        val nCols = costMatrix[0].size
        if (nCols == 0) return IntArray(nRows) { -1 }

        val n = maxOf(nRows, nCols)
        val padCost = 1e6f

        // Build square 1-indexed matrix [1..n][1..n]
        val a = Array(n + 1) { FloatArray(n + 1) { padCost } }
        for (i in 0 until nRows) {
            val gate = maxGatingCost.getOrElse(i) { 0.20f }
            for (j in 0 until nCols) {
                val c = costMatrix[i][j]
                // If cost exceeds gate, penalize heavily so it won't be matched
                a[i + 1][j + 1] = if (c <= gate) c else padCost
            }
        }

        val u = FloatArray(n + 1)
        val v = FloatArray(n + 1)
        val p = IntArray(n + 1)
        val way = IntArray(n + 1)

        for (i in 1..n) {
            p[0] = i
            var j0 = 0
            val minv = FloatArray(n + 1) { Float.POSITIVE_INFINITY }
            val used = BooleanArray(n + 1) { false }

            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = Float.POSITIVE_INFINITY
                var j1 = 0
                for (j in 1..n) {
                    if (!used[j]) {
                        val cur = a[i0][j] - u[i0] - v[j]
                        if (cur < minv[j]) {
                            minv[j] = cur
                            way[j] = j0
                        }
                        if (minv[j] < delta) {
                            delta = minv[j]
                            j1 = j
                        }
                    }
                }
                for (j in 0..n) {
                    if (used[j]) {
                        u[p[j]] += delta
                        v[j] -= delta
                    } else {
                        minv[j] -= delta
                    }
                }
                j0 = j1
            } while (p[j0] != 0)

            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }

        val assignment = IntArray(nRows) { -1 }
        for (j in 1..nCols) {
            val rowIdx = p[j] - 1
            val colIdx = j - 1
            if (rowIdx in 0 until nRows) {
                val gate = maxGatingCost.getOrElse(rowIdx) { 0.20f }
                if (costMatrix[rowIdx][colIdx] <= gate) {
                    assignment[rowIdx] = colIdx
                }
            }
        }
        return assignment
    }
}
