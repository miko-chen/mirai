/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("RESULT_CLASS_IN_RETURN_TYPE") // inline ABI not stable but we don't care about internal ABI

package net.mamoe.mirai.internal.contact

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.EventCancelledException
import net.mamoe.mirai.event.events.MessagePreSendEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.toMessageChain

/**
 * Called only in 'public' apis.
 */
internal suspend fun <C : Contact> C.broadcastMessagePreSendEvent(
    message: Message,
    isMiraiInternal: Boolean,
    eventConstructor: (C, Message) -> MessagePreSendEvent,
): MessageChain {
    if (isMiraiInternal) return message.toMessageChain()
    var eventName: String? = null
    return kotlin.runCatching {
        eventConstructor(this, message).also {
            eventName = it.javaClass.simpleName
        }.broadcast()
    }.onSuccess {
        check(!it.isCancelled) {
            throw EventCancelledException("cancelled by $eventName")
        }
    }.getOrElse {
        eventName = eventName ?: (this@broadcastMessagePreSendEvent.javaClass.simpleName + "MessagePreSendEvent")
        throw EventCancelledException("exception thrown when broadcasting $eventName", it)
    }.message.toMessageChain()
}


internal enum class SendMessageStep {
    FIRST {
        override fun nextStepOrNull(): SendMessageStep {
            return LONG_MESSAGE
        }
    },
    LONG_MESSAGE {
        override fun nextStepOrNull(): SendMessageStep {
            return FRAGMENTED
        }
    },
    FRAGMENTED {
        override fun nextStepOrNull(): SendMessageStep? {
            return null
        }
    };

    abstract fun nextStepOrNull(): SendMessageStep?
}
