/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.utils

import java.util.*


public fun <T> Collection<T>.asImmutable(): Collection<T> {
    return when (this) {
        is List<T> -> asImmutable()
        is Set<T> -> asImmutable()
        else -> Collections.unmodifiableCollection(this)
    }
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <T> Collection<T>.asImmutableStrict(): Collection<T> {
    return Collections.unmodifiableCollection(this)
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <T> List<T>.asImmutable(): List<T> {
    return Collections.unmodifiableList(this)
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <T> Set<T>.asImmutable(): Set<T> {
    return Collections.unmodifiableSet(this)
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <K, V> Map<K, V>.asImmutable(): Map<K, V> {
    return Collections.unmodifiableMap(this)
}

@Suppress("FunctionName")
public actual fun <K : Any, V> ConcurrentHashMap(): MutableMap<K, V> {
    return java.util.concurrent.ConcurrentHashMap()
}