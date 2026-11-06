
// Top-level build file. Plugin versions are pinned so the wrapper and AGP agree.
// The Wrapper should point at the -bin distribution, not -all, per Gradle's
// performance guidance: the -bin archive is the recommended size/performance
// trade-off for CI [reference:0].
plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23" apply false
}
