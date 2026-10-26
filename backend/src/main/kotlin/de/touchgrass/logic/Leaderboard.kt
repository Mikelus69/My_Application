package de.touchgrass.logic

data class Score(val userId: Long, val username: String, val value: Int)

data class RankedScore(val rank: Int, val score: Score)

object Leaderboard {
    const val TOP = 50

    fun rank(scores: List<Score>): List<RankedScore> {
        val sorted = scores.sortedWith(compareByDescending<Score> { it.value }.thenBy { it.username.lowercase() })
        var rank = 0
        return sorted.mapIndexed { index, score ->
            if (index == 0 || sorted[index - 1].value != score.value) rank = index + 1
            RankedScore(rank, score)
        }
    }
}
