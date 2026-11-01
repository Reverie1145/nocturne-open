package cn.reverie.util.rotation

enum class Priority(val level: Int) {
    VERY_LOW(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    VERY_HIGH(4);

    fun isLowerThan(other: Priority): Boolean = level < other.level
}
