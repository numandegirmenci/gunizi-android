package com.numandegirmenci.belgeizi.engine

import kotlin.math.abs

/**
 * Conservative decision policy. Never rotate if printed text is not
 * consistently tilted. Pure Kotlin for deterministic JVM regression tests.
 */
object SkewDecision {
    fun reliableAngle(degrees: List<Float>): Float? {
        val candidates = degrees.filter { it.isFinite() && abs(it) <= 8f }.sorted()
        if (candidates.size < 3) return null
        val center = candidates[candidates.size / 2]
        val inliers = candidates.filter { abs(it - center) < 1.15f }.sorted()
        if (inliers.size < 3 || inliers.size * 3 < candidates.size * 2) return null
        val answer = inliers[inliers.size / 2]
        if (abs(answer) < 0.55f || abs(answer) > 7f) return null
        return answer
    }
}
