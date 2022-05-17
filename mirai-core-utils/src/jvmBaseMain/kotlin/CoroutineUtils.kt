/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

@file:JvmMultifileClass
@file:JvmName("MiraiUtils")

package net.mamoe.mirai.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

public actual suspend inline fun <R> runBIO(
    noinline block: () -> R,
): R = runInterruptible(context = Dispatchers.IO, block = block)

public actual suspend inline fun <T, R> T.runBIO(
    crossinline block: T.() -> R,
): R = runInterruptible(context = Dispatchers.IO, block = { block() })
