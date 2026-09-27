package kotlinx.datetime;

/**
 * dhyantoast 0.0.1 兼容垫片（**仅 Android/JVM 需要**，不要删）。
 *
 * 背景：`io.github.androidpoet:dhyantoast:0.0.1` 是针对 kotlinx-datetime **0.6.x** 编译的，
 * 其 ToastHost 的拖拽手势字节码里引用了 `kotlinx.datetime.Clock` / `Clock$System` /
 * `kotlinx.datetime.Instant` 这几个**类**；而本项目用的 kotlinx-datetime **0.7.x**
 * 已把它们改成 `kotlin.time.*` 的 typealias（运行时不再有这些类文件）。
 *
 * 结果：不补这几个类，拖动 Toast 时会抛 `NoClassDefFoundError: kotlinx/datetime/Clock$System`。
 * 这里按 0.6.x 的形状补上最小实现（只提供 dhyantoast 用到的 `now()` /
 * `toEpochMilliseconds()`），供其字节码链接；iOS 走 klib（typealias 可正常解析）不需要垫片。
 *
 * 上游若发布适配 0.7.x 的版本，可删除本目录。
 */
public interface Clock {

    Instant now();

    /** 0.6.x 里的 `Clock.System` 单例对象（Kotlin object → INSTANCE 静态字段）。 */
    final class System implements Clock {

        public static final System INSTANCE = new System();

        private System() {
        }

        @Override
        public Instant now() {
            return Instant.fromEpochMilliseconds(java.lang.System.currentTimeMillis());
        }
    }
}
