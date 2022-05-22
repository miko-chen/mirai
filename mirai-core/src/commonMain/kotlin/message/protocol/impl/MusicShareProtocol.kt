/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.internal.message.protocol.impl

import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.internal.message.protocol.MessageProtocol
import net.mamoe.mirai.internal.message.protocol.ProcessorCollector
import net.mamoe.mirai.internal.message.protocol.encode.MessageEncoder
import net.mamoe.mirai.internal.message.protocol.encode.MessageEncoderContext
import net.mamoe.mirai.internal.message.protocol.outgoing.OutgoingMessagePipelineContext
import net.mamoe.mirai.internal.message.protocol.outgoing.OutgoingMessagePipelineContext.Companion.CONTACT
import net.mamoe.mirai.internal.message.protocol.outgoing.OutgoingMessageSender
import net.mamoe.mirai.internal.message.source.createMessageReceipt
import net.mamoe.mirai.internal.network.protocol.packet.chat.MusicSharePacket
import net.mamoe.mirai.message.data.MessageSourceKind
import net.mamoe.mirai.message.data.MusicShare
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.content

internal class MusicShareProtocol : MessageProtocol() {
    override fun ProcessorCollector.collectProcessorsImpl() {
        add(Encoder())
        // no decoder. refined from LightApp
//        add(Decoder())

        add(Sender())
    }

    private class Encoder : MessageEncoder<MusicShare> {
        override suspend fun MessageEncoderContext.process(data: MusicShare) {
            markAsConsumed()
            // 只有在 QuoteReply 的 source 里才会进行 MusicShare 转换, 因此可以转 PT.
            // 发送消息时会被特殊处理
            processAlso(PlainText(data.content))
        }
    }

    private class Sender : OutgoingMessageSender {
        override suspend fun OutgoingMessagePipelineContext.process() {
            val contact = attributes[CONTACT]
            val bot = contact.bot
            val musicShare = currentMessageChain[MusicShare] ?: return

            val packet = MusicSharePacket(
                bot.client, musicShare, contact.id,
                targetKind = if (contact is Group) MessageSourceKind.GROUP else MessageSourceKind.FRIEND // always FRIEND
            )
            val result = bot.network.sendAndExpect(packet)
            result.pkg.checkSuccess("send music share")

            val strategy = attributes[OutgoingMessagePipelineContext.PROTOCOL_STRATEGY]
            val source = strategy.constructSourceForSpecialMessage(currentMessageChain, 3116)
            source.tryEnsureSequenceIdAvailable()

            collect(source.createMessageReceipt(contact, true))
        }
    }
}