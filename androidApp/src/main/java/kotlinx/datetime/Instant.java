package kotlinx.datetime;

/**
 * dhyantoast 0.0.1 兼容垫片（**仅 Android/JVM 需要**，不要删）——与同目录的 [Clock] 配套。
 *
 * 只实现 dhyantoast 字节码用到的方法：`toEpochMilliseconds()`（拖拽测速用）。
 * 详见 Clock.java 的说明。
 */
public final class Instant {

    private final long epochMilliseconds;

    private Instant(long epochMilliseconds) {
        this.epochMilliseconds = epochMilliseconds;
    }

    public long toEpochMilliseconds() {
        return epochMilliseconds;
    }

    public static Instant fromEpochMilliseconds(long epochMilliseconds) {
        return new Instant(epochMilliseconds);
    }
}
