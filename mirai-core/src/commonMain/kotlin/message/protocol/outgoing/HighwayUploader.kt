/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.internal.message.protocol.outgoing

import net.mamoe.mirai.internal.contact.AbstractContact
import net.mamoe.mirai.internal.contact.nickIn
import net.mamoe.mirai.internal.message.data.MultiMsgUploader
import net.mamoe.mirai.internal.message.protocol.MessageProtocolFacade
import net.mamoe.mirai.internal.message.source.ensureSequenceIdAvailable
import net.mamoe.mirai.internal.network.component.ComponentKey
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.MessageChain

internal interface HighwayUploader {
    suspend fun uploadMessages(
        contact: AbstractContact,
        strategy: MessageProtocolStrategy<*>,
        nodes: Collection<ForwardMessage.INode>,
        isLong: Boolean,
        facade: MessageProtocolFacade = MessageProtocolFacade,
        senderName: String = contact.bot.nickIn(contact),
    ): String {
        nodes.forEach { it.messageChain.ensureSequenceIdAvailable() }

        val uploader = MultiMsgUploader(
            client = contact.bot.client,
            isLong = isLong,
            facade = facade,
            contact = contact,
            senderName = senderName,
            strategy = strategy
        ).also { it.emitMain(nodes) }

        return uploader.uploadAndReturnResId()
    }

    suspend fun uploadLongMessage(
        contact: AbstractContact,
        strategy: MessageProtocolStrategy<*>,
        chain: MessageChain,
        timeSeconds: Int,
        senderName: String = contact.bot.nickIn(contact),
    ): String {
        val bot = contact.bot
        return uploadMessages(
            contact,
            strategy,
            listOf(
                ForwardMessage.Node(
                    senderId = bot.id,
                    time = timeSeconds,
                    messageChain = chain,
                    senderName = senderName
                )
            ),
            true,
            senderName = senderName
        )
    }

    companion object : ComponentKey<HighwayUploader>

    object Default : HighwayUploader
}