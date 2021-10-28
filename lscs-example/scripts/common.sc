

// Used when identifying a single segment for a command - may be 1 - a22 or ALL
val segmentIdKey: Key[String] = KeyType.StringKey.make("SegmentId")
val ALL_SEGMENTS              = "ALL"

// Used to provide one or more ranges to receive a command
val segmentRangeKey: Key[String] = KeyType.StringKey.make("SegmentRanges")
// Shared keys
val actuatorIdKey: Key[Int] = KeyType.IntKey.make("ACT_ID")
// Used by every command
def valuesToString[A](items: Array[A]): String = items.mkString("(", ",", ")")

val AllActuators = Set(1, 2, 3)