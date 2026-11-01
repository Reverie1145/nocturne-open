package cn.reverie.render.hud

import kotlin.math.abs

/** Geometry-only magnetic alignment resolver, kept independent of Minecraft for deterministic checks. */
internal object HudSnapEngine {
    data class Bounds(val x: Float, val y: Float, val width: Float, val height: Float)

    data class Result(val x: Float, val y: Float, val lines: List<SnapLine>)

    private data class Candidate(
        val target: Float,
        val guidePosition: Float,
        val isCenter: Boolean = false,
        val label: String? = null,
        val other: Bounds? = null,
        val fullSpan: Boolean = false,
        val priority: Int = 0
    )

    fun resolve(
        moving: Bounds,
        desiredX: Float,
        desiredY: Float,
        screenWidth: Float,
        screenHeight: Float,
        others: List<Bounds>,
        snapDistance: Float,
        enabled: Boolean = true,
        edgePadding: Float = 8f,
        widgetGap: Float = 4f
    ): Result {
        val maxX = (screenWidth - moving.width).coerceAtLeast(0f)
        val maxY = (screenHeight - moving.height).coerceAtLeast(0f)
        val baseX = desiredX.coerceIn(0f, maxX)
        val baseY = desiredY.coerceIn(0f, maxY)

        if (!enabled || snapDistance < 0f) return Result(baseX, baseY, emptyList())

        val xCandidates = mutableListOf(
            Candidate(edgePadding, edgePadding, label = "Left · 8px", fullSpan = true, priority = 1),
            Candidate(screenWidth - edgePadding - moving.width, screenWidth - edgePadding, label = "Right · 8px", fullSpan = true, priority = 1),
            Candidate((screenWidth - moving.width) * 0.5f, screenWidth * 0.5f, true, "Center X", fullSpan = true)
        )
        val yCandidates = mutableListOf(
            Candidate(edgePadding, edgePadding, label = "Top · 8px", fullSpan = true, priority = 1),
            Candidate(screenHeight - edgePadding - moving.height, screenHeight - edgePadding, label = "Bottom · 8px", fullSpan = true, priority = 1),
            Candidate((screenHeight - moving.height) * 0.5f, screenHeight * 0.5f, true, "Center Y", fullSpan = true)
        )

        for (other in others) {
            val otherCenterX = other.x + other.width * 0.5f
            val otherCenterY = other.y + other.height * 0.5f

            xCandidates += Candidate(other.x, other.x, other = other, priority = 2)
            xCandidates += Candidate(other.x + other.width - moving.width, other.x + other.width, other = other, priority = 2)
            xCandidates += Candidate(otherCenterX - moving.width * 0.5f, otherCenterX, true, "Center X", other, priority = 2)
            xCandidates += Candidate(other.x + other.width + widgetGap, other.x + other.width + widgetGap, other = other, priority = 3)
            xCandidates += Candidate(other.x - moving.width - widgetGap, other.x, other = other, priority = 3)

            yCandidates += Candidate(other.y, other.y, other = other, priority = 2)
            yCandidates += Candidate(other.y + other.height - moving.height, other.y + other.height, other = other, priority = 2)
            yCandidates += Candidate(otherCenterY - moving.height * 0.5f, otherCenterY, true, "Center Y", other, priority = 2)
            yCandidates += Candidate(other.y + other.height + widgetGap, other.y + other.height + widgetGap, other = other, priority = 3)
            yCandidates += Candidate(other.y - moving.height - widgetGap, other.y, other = other, priority = 3)
        }

        val xMatch = closestCandidate(xCandidates, baseX, maxX, snapDistance)
        val yMatch = closestCandidate(yCandidates, baseY, maxY, snapDistance)
        val x = xMatch?.target ?: baseX
        val y = yMatch?.target ?: baseY
        val lines = buildList {
            xMatch?.let { candidate ->
                val other = candidate.other
                add(
                    SnapLine(
                        isVertical = true,
                        position = candidate.guidePosition,
                        start = if (candidate.fullSpan || other == null) 0f else minOf(y, other.y),
                        end = if (candidate.fullSpan || other == null) screenHeight else maxOf(y + moving.height, other.y + other.height),
                        isCenter = candidate.isCenter,
                        label = candidate.label
                    )
                )
            }
            yMatch?.let { candidate ->
                val other = candidate.other
                add(
                    SnapLine(
                        isVertical = false,
                        position = candidate.guidePosition,
                        start = if (candidate.fullSpan || other == null) 0f else minOf(x, other.x),
                        end = if (candidate.fullSpan || other == null) screenWidth else maxOf(x + moving.width, other.x + other.width),
                        isCenter = candidate.isCenter,
                        label = candidate.label
                    )
                )
            }
        }
        return Result(x, y, lines)
    }

    private fun closestCandidate(
        candidates: List<Candidate>,
        position: Float,
        maxPosition: Float,
        snapDistance: Float
    ): Candidate? = candidates.asSequence()
        .filter { it.target in 0f..maxPosition && abs(it.target - position) <= snapDistance }
        .minWithOrNull(compareBy<Candidate> { abs(it.target - position) }.thenBy { it.priority })
}
