/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.internal.contact

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.nextEvent
import net.mamoe.mirai.internal.asQQAndroidBot
import net.mamoe.mirai.internal.getMiraiImpl
import net.mamoe.mirai.internal.message.*
import net.mamoe.mirai.internal.message.flags.DontAsLongMessage
import net.mamoe.mirai.internal.message.flags.ForceAsLongMessage
import net.mamoe.mirai.internal.message.flags.IgnoreLengthCheck
import net.mamoe.mirai.internal.message.image.FriendImage
import net.mamoe.mirai.internal.message.image.OfflineGroupImage
import net.mamoe.mirai.internal.message.source.OnlineMessageSourceToFriendImpl
import net.mamoe.mirai.internal.message.source.OnlineMessageSourceToGroupImpl
import net.mamoe.mirai.internal.message.source.createMessageReceipt
import net.mamoe.mirai.internal.message.source.ensureSequenceIdAvailable
import net.mamoe.mirai.internal.network.Packet
import net.mamoe.mirai.internal.network.QQAndroidClient
import net.mamoe.mirai.internal.network.components.ClockHolder.Companion.clock
import net.mamoe.mirai.internal.network.components.MessageSvcSyncer
import net.mamoe.mirai.internal.network.handler.logger
import net.mamoe.mirai.internal.network.notice.group.GroupMessageProcessor.SendGroupMessageReceipt
import net.mamoe.mirai.internal.network.notice.priv.PrivateMessageProcessor.SendPrivateMessageReceipt
import net.mamoe.mirai.internal.network.protocol.data.proto.MsgComm
import net.mamoe.mirai.internal.network.protocol.packet.OutgoingPacket
import net.mamoe.mirai.internal.network.protocol.packet.chat.FileManagement
import net.mamoe.mirai.internal.network.protocol.packet.chat.MusicSharePacket
import net.mamoe.mirai.internal.network.protocol.packet.chat.receive.*
import net.mamoe.mirai.internal.utils.ImagePatcher
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.castOrNull
import net.mamoe.mirai.utils.currentTimeSeconds

/**
 * 处理 mirai 消息系统 `Message` 到协议数据结构的转换.
 *
 * 外部调用 [sendMessageImpl]
 */
internal abstract class SendMessageHandler<C : Contact> {
    abstract val contact: C
    abstract val senderName: String

    val messageSourceKind: MessageSourceKind
        get() {
            return when (contact) {
                is Group -> MessageSourceKind.GROUP
                is Friend -> MessageSourceKind.FRIEND
                is Member -> MessageSourceKind.TEMP
                is Stranger -> MessageSourceKind.STRANGER
                else -> error("Unsupported contact: $contact")
            }
        }

    val bot get() = contact.bot.asQQAndroidBot()

    val targetUserUin: Long? get() = contact.castOrNull<User>()?.uin
    val targetGroupUin: Long? get() = contact.castOrNull<Group>()?.uin
    val targetGroupCode: Long? get() = contact.castOrNull<Group>()?.groupCode

    val targetOtherClientBotUin: Long? get() = contact.castOrNull<OtherClient>()?.bot?.id

    val targetUin: Long get() = targetGroupUin ?: targetOtherClientBotUin ?: contact.id

    val groupInfo: MsgComm.GroupInfo?
        get() = if (isToGroup) MsgComm.GroupInfo(
            groupCode = targetGroupCode!!,
            groupCard = senderName // Cinnamon
        ) else null

    // For ForwardMessage display
    val ForwardMessage.INode.groupInfo: MsgComm.GroupInfo
        get() = MsgComm.GroupInfo(
            groupCode = if (isToGroup) targetGroupCode!! else 0,
            groupCard = senderName
        )

    val isToGroup: Boolean get() = contact is Group

    suspend fun MessageChain.convertToLongMessageIfNeeded(
        step: SendMessageStep,
    ): MessageChain {
        suspend fun sendLongImpl(): MessageChain {
            val resId = uploadLongMessageHighway(this)
            return this + RichMessage.longMessage(
                brief = takeContent(27),
                resId = resId,
                timeSeconds = currentTimeSeconds()
            ) // LongMessageInternal replaces all contents and preserves metadata
        }
        return when (step) {
            SendMessageStep.FIRST -> {
                // 只需要在第一次发送的时候验证长度
                // 后续重试直接跳过
                if (contains(ForceAsLongMessage)) {
                    return sendLongImpl()
                }

                if (!contains(IgnoreLengthCheck)) {
                    verifyLength(this, contact)
                }

                this
            }
            SendMessageStep.LONG_MESSAGE -> {
                if (contains(DontAsLongMessage)) this // fragmented
                else sendLongImpl()
            }
            SendMessageStep.FRAGMENTED -> this
        }
    }

    /**
     * Final process. Convert transformed message to protocol internals and transfer to server
     */
    suspend fun sendMessagePacket(
        originalMessage: Message,
        transformedMessage: MessageChain,
        finalMessage: MessageChain,
        isMiraiInternal: Boolean,
        step: SendMessageStep,
    ): MessageReceipt<C> {
        bot.components[MessageSvcSyncer].joinSync()

        val group = contact

        var source: Deferred<OnlineMessageSource.Outgoing>? = null

        bot.network.run {
            sendMessageMultiProtocol(
                bot.client, finalMessage,
                fragmented = step == SendMessageStep.FRAGMENTED
            ) { source = it }.forEach { packet ->

                when (val resp = packet.sendAndExpect<Packet>()) {
                    is MessageSvcPbSendMsg.Response -> {
                        if (resp is MessageSvcPbSendMsg.Response.MessageTooLarge) {
                            return when (step) {
                                SendMessageStep.FIRST -> {
                                    sendMessageImpl(
                                        originalMessage,
                                        transformedMessage,
                                        isMiraiInternal,
                                        SendMessageStep.LONG_MESSAGE,
                                    )
                                }
                                SendMessageStep.LONG_MESSAGE -> {
                                    sendMessageImpl(
                                        originalMessage,
                                        transformedMessage,
                                        isMiraiInternal,
                                        SendMessageStep.FRAGMENTED,
                                    )

                                }
                                else -> {
                                    throw MessageTooLargeException(
                                        group,
                                        originalMessage,
                                        finalMessage,
                                        "Message '${finalMessage.content.take(10)}' is too large."
                                    )
                                }
                            }
                        }
                        if (resp is MessageSvcPbSendMsg.Response.ServiceUnavailable) {
                            throw IllegalStateException("Send message to $contact failed, server service is unavailable.")
                        }
                        if (resp is MessageSvcPbSendMsg.Response.Failed) {
                            val contact = contact
                            when (resp.resultType) {
                                120 -> if (contact is Group) throw BotIsBeingMutedException(contact, originalMessage)
                                121 -> if (AtAll in finalMessage) throw SendMessageFailedException(
                                    contact,
                                    SendMessageFailedException.Reason.AT_ALL_LIMITED,
                                    originalMessage
                                )
                                299 -> if (contact is Group) throw SendMessageFailedException(
                                    contact,
                                    SendMessageFailedException.Reason.GROUP_CHAT_LIMITED,
                                    originalMessage
                                )
                            }
                        }
                        check(resp is MessageSvcPbSendMsg.Response.SUCCESS) {
                            "Send message failed: $resp"
                        }
                    }
                    is MusicSharePacket.Response -> {
                        resp.pkg.checkSuccess("send music share")

                        source = CompletableDeferred(constructSourceForSpecialMessage(finalMessage, 3116))
                    }
                    //                    is CommonOidbResponse<*> -> {
                    //                        when (resp.toResult("send message").getOrThrow()) {
                    //                            is Oidb0x6d9.FeedsRspBody -> {
                    //                            }
                    //                        }
                    //                    }
                }
            }

            val sourceAwait = source?.await() ?: error("Internal error: source is not initialized")

            try {
                sourceAwait.ensureSequenceIdAvailable()
            } catch (e: Exception) {
                bot.network.logger.warning(
                    "Timeout awaiting sequenceId for message(${finalMessage.content.take(10)}). Some features may not work properly",
                    e
                )
            }

            return sourceAwait.createMessageReceipt(contact, true)
        }
    }

    private suspend fun sendMessageMultiProtocol(
        client: QQAndroidClient,
        message: MessageChain,
        fragmented: Boolean,
        sourceCallback: (Deferred<OnlineMessageSource.Outgoing>) -> Unit,
    ): List<OutgoingPacket> {
        message.takeSingleContent<MusicShare>()?.let { musicShare ->
            return listOf(
                MusicSharePacket(
                    client, musicShare, contact.id,
                    targetKind = if (isToGroup) MessageSourceKind.GROUP else MessageSourceKind.FRIEND // always FRIEND
                )
            )
        }

        message.takeSingleContent<FileMessage>()?.let { file ->
            file.checkIsImpl()
            sourceCallback(contact.async { constructSourceForSpecialMessage(message, 2021) })
            return listOf(FileManagement.Feed(client, contact.id, file.busId, file.id))
        }

        return messageSvcSendMessage(client, contact, message, fragmented, sourceCallback)
    }

    abstract val messageSvcSendMessage: (
        client: QQAndroidClient,
        contact: C,
        message: MessageChain,
        fragmented: Boolean,
        sourceCallback: (Deferred<OnlineMessageSource.Outgoing>) -> Unit,
    ) -> List<OutgoingPacket>

    abstract suspend fun constructSourceForSpecialMessage(
        finalMessage: MessageChain,
        fromAppId: Int,
    ): OnlineMessageSource.Outgoing

    open suspend fun uploadLongMessageHighway(
        chain: MessageChain,
    ): String = with(contact) {
        return getMiraiImpl().uploadMessageHighway(
            bot, this@SendMessageHandler,
            listOf(
                ForwardMessage.Node(
                    senderId = bot.id,
                    time = currentTimeSeconds().toInt(),
                    messageChain = chain,
                    senderName = bot.nick
                )
            ),
            true
        )
    }

    open suspend fun preConversionTransformedMessage(message: Message): Message = message
    open suspend fun conversionMessageChain(chain: MessageChain): MessageChain = chain

    open suspend fun postTransformActions(chain: MessageChain) {

    }
}

/**
 * 处理需要 `suspend` 操作的消息转换. 这个转换只会在发送消息时进行, 而不会在处理合并转发 [net.mamoe.mirai.internal.network.protocol.packet.chat.calculateValidationData] 等其他操作时进行.
 * 在发包前还会进行最后的 [net.mamoe.mirai.internal.message.toRichTextElems] 转换, 这个转换会为所有操作使用.
 *
 * - [ForwardMessage] -> [ForwardMessageInternal] (by uploading through highway)
 * - ... any others for future
 */
internal suspend fun <C : Contact> SendMessageHandler<C>.transformSpecialMessages(message: Message): MessageChain {
    suspend fun processForwardMessage(
        forward: ForwardMessage,
    ): ForwardMessageInternal {
        if (!(message is MessageChain && message.contains(IgnoreLengthCheck))) {
            check(forward.nodeList.size <= 200) {
                throw MessageTooLargeException(
                    contact, forward, forward,
                    "ForwardMessage allows up to 200 nodes, but found ${forward.nodeList.size}"
                )
            }
            val tmp = ArrayList<SingleMessage>(
                forward.nodeList.sumOf { it.messageChain.size }
            )
            forward.nodeList.forEach { tmp.addAll(it.messageChain) }

            // toMessageChain will lose some element
            @Suppress("INVISIBLE_MEMBER")
            createMessageChainImplOptimized(tmp).verifyLength(forward, contact)
        }

        val resId = getMiraiImpl().uploadMessageHighway(
            bot = contact.bot,
            sendMessageHandler = this,
            message = forward.nodeList,
            isLong = false,
        )
        return RichMessage.forwardMessage(
            resId = resId,
            fileName = currentTimeSeconds().toString(),
            forwardMessage = forward,
        )
    }


    // loses MessageMetadata and other message types but fine for now.
    return message.takeSingleContent<ForwardMessage>()?.let { processForwardMessage(it) }?.toMessageChain()
        ?: message.toMessageChain()
}

/**
 * Send a message, and covert messages
 *
 * Don't recall this function.
 */
internal suspend fun <C : Contact> SendMessageHandler<C>.sendMessage(
    originalMessage: Message,
    transformedMessage: Message,
    isMiraiInternal: Boolean,
    step: SendMessageStep,
): MessageReceipt<C> = sendMessageImpl(
    originalMessage,
    conversionMessageChain(
        transformSpecialMessages(
            preConversionTransformedMessage(transformedMessage)
        )
    ),
    isMiraiInternal,
    step
)

/**
 * Might be recalled with [transformedMessage] `is` [LongMessageInternal] if length estimation failed (sendMessagePacket)
 */
private suspend fun <C : Contact> SendMessageHandler<C>.sendMessageImpl(
    originalMessage: Message,
    transformedMessage: MessageChain,
    isMiraiInternal: Boolean,
    step: SendMessageStep,
): MessageReceipt<C> { // Result cannot be in interface.
    if (!isMiraiInternal && step == SendMessageStep.FIRST) {
        transformedMessage.verifySendingValid()
    }
    val chain = transformedMessage.convertToLongMessageIfNeeded(step)

    chain.findIsInstance<QuoteReply>()?.source?.ensureSequenceIdAvailable()

    postTransformActions(chain)

    return sendMessagePacket(originalMessage, transformedMessage, chain, isMiraiInternal, step)
}

internal sealed class UserSendMessageHandler<C : AbstractUser>(
    override val contact: C,
) : SendMessageHandler<C>() {
    override val senderName: String get() = bot.nick

    override suspend fun constructSourceForSpecialMessage(
        finalMessage: MessageChain,
        fromAppId: Int,
    ): OnlineMessageSource.Outgoing {
        throw UnsupportedOperationException("Sending MusicShare or FileMessage to User is not yet supported")
    }
}

internal class FriendSendMessageHandler(
    contact: FriendImpl,
) : UserSendMessageHandler<FriendImpl>(contact) {
    override val messageSvcSendMessage: (client: QQAndroidClient, contact: FriendImpl, message: MessageChain, fragmented: Boolean, sourceCallback: (Deferred<OnlineMessageSource.Outgoing>) -> Unit) -> List<OutgoingPacket> =
        MessageSvcPbSendMsg::createToFriend

    override suspend fun constructSourceForSpecialMessage(
        finalMessage: MessageChain,
        fromAppId: Int
    ): OnlineMessageSource.Outgoing {

        val receipt: SendPrivateMessageReceipt = withTimeoutOrNull(3000) {
            GlobalEventChannel.parentScope(this).nextEvent(EventPriority.MONITOR) {
                it.bot === bot && it.fromAppId == fromAppId
            }
        } ?: SendPrivateMessageReceipt.EMPTY

        return OnlineMessageSourceToFriendImpl(
            internalIds = intArrayOf(receipt.messageRandom),
            sequenceIds = intArrayOf(receipt.sequenceId),
            sender = bot,
            target = contact,
            time = bot.clock.server.currentTimeSeconds().toInt(),
            originalMessage = finalMessage
        )
    }
}

internal class StrangerSendMessageHandler(
    contact: StrangerImpl,
) : UserSendMessageHandler<StrangerImpl>(contact) {
    override val messageSvcSendMessage: (client: QQAndroidClient, contact: StrangerImpl, message: MessageChain, fragmented: Boolean, sourceCallback: (Deferred<OnlineMessageSource.Outgoing>) -> Unit) -> List<OutgoingPacket> =
        MessageSvcPbSendMsg::createToStranger
}

internal class GroupTempSendMessageHandler(
    contact: NormalMemberImpl,
) : UserSendMessageHandler<NormalMemberImpl>(contact) {
    override val messageSvcSendMessage: (client: QQAndroidClient, contact: NormalMemberImpl, message: MessageChain, fragmented: Boolean, sourceCallback: (Deferred<OnlineMessageSource.Outgoing>) -> Unit) -> List<OutgoingPacket> =
        MessageSvcPbSendMsg::createToTemp
}

internal open class GroupSendMessageHandler(
    override val contact: GroupImpl,
) : SendMessageHandler<GroupImpl>() {
    override val messageSvcSendMessage: (client: QQAndroidClient, contact: GroupImpl, message: MessageChain, fragmented: Boolean, sourceCallback: (Deferred<OnlineMessageSource.Outgoing>) -> Unit) -> List<OutgoingPacket> =
        MessageSvcPbSendMsg::createToGroup
    override val senderName: String
        get() = contact.botAsMember.nameCardOrNick

    override suspend fun conversionMessageChain(chain: MessageChain): MessageChain = chain.map { element ->
        when (element) {
            is OfflineGroupImage -> {
                contact.fixImageFileId(element)
                element
            }
            is FriendImage -> {
                contact.updateFriendImageForGroupMessage(element)
            }
            else -> element
        }
    }.toMessageChain()

    override suspend fun constructSourceForSpecialMessage(
        finalMessage: MessageChain,
        fromAppId: Int,
    ): OnlineMessageSource.Outgoing {

        val receipt: SendGroupMessageReceipt = withTimeoutOrNull(3000) {
            GlobalEventChannel.parentScope(this).nextEvent(EventPriority.MONITOR) {
                it.bot === bot && it.fromAppId == fromAppId
            }
        } ?: SendGroupMessageReceipt.EMPTY

        return OnlineMessageSourceToGroupImpl(
            contact,
            internalIds = intArrayOf(receipt.messageRandom),
            providedSequenceIds = intArrayOf(receipt.sequenceId),
            sender = bot,
            target = contact,
            time = bot.clock.server.currentTimeSeconds().toInt(),
            originalMessage = finalMessage
        )
    }

    companion object {
        private suspend fun GroupImpl.fixImageFileId(image: OfflineGroupImage) {
            bot.components[ImagePatcher].patchOfflineGroupImage(this, image)
        }

        private suspend fun GroupImpl.updateFriendImageForGroupMessage(image: FriendImage): OfflineGroupImage {
            return bot.components[ImagePatcher].patchFriendImageToGroupImage(this, image)
        }
    }
}
