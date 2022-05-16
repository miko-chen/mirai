/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_OVERRIDE", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package net.mamoe.mirai.internal.message.source

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.syncFromEvent
import net.mamoe.mirai.internal.contact.uin
import net.mamoe.mirai.internal.message.MessageSourceInternal
import net.mamoe.mirai.internal.message.MessageSourceSerializerImpl
import net.mamoe.mirai.internal.message.OutgoingMessageSourceInternal
import net.mamoe.mirai.internal.message.toRichTextElems
import net.mamoe.mirai.internal.network.notice.group.GroupMessageProcessor.SendGroupMessageReceipt
import net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody
import net.mamoe.mirai.internal.network.protocol.data.proto.MsgComm
import net.mamoe.mirai.internal.network.protocol.data.proto.SourceMsg
import net.mamoe.mirai.internal.utils.io.serialization.toByteArray
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.OnlineMessageSource
import net.mamoe.mirai.message.data.visitor.MessageVisitor
import net.mamoe.mirai.utils.toLongUnsigned
import java.util.concurrent.atomic.AtomicBoolean


private fun <T> T.toJceDataImpl(subject: ContactOrBot?): ImMsgBody.SourceMsg
        where T : MessageSourceInternal, T : MessageSource {

    val elements = originalMessage.toRichTextElems(subject, withGeneralFlags = true)

    val pdReserve = SourceMsg.ResvAttr(
        origUids = sequenceIds.zip(internalIds)
            .map { (seq, internal) -> seq.toLong().shl(32) or internal.toLongUnsigned() }
    )

    return ImMsgBody.SourceMsg(
        origSeqs = sequenceIds,
        senderUin = fromId,
        toUin = targetId,
        flag = 1,
        elems = elements,
        type = 0,
        time = time,
        pbReserve = pdReserve.toByteArray(SourceMsg.ResvAttr.serializer()),
        srcMsg = MsgComm.Msg(
            msgHead = MsgComm.MsgHead(
                fromUin = fromId, // qq
                toUin = targetId, // group
                msgType = 9, // 82?
                c2cCmd = 11,
                msgSeq = sequenceIds.first(),
                msgTime = time,
                msgUid = pdReserve.origUids!!.first(),
                // groupInfo = MsgComm.GroupInfo(groupCode = delegate.msgHead.groupInfo.groupCode),
                isSrcMsg = true
            ),
            msgBody = ImMsgBody.MsgBody(
                richText = ImMsgBody.RichText(
                    elems = elements.toMutableList().also {
                        if (it.last().elemFlags2 == null) it.add(ImMsgBody.Elem(elemFlags2 = ImMsgBody.ElemFlags2()))
                    }
                )
            )
        ).toByteArray(MsgComm.Msg.serializer())
    )
}

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(OnlineMessageSourceToFriendImpl.Serializer::class)
internal class OnlineMessageSourceToFriendImpl(
    override val sequenceIds: IntArray,
    override val internalIds: IntArray,
    override val time: Int,
    override var originalMessage: MessageChain,
    override val sender: Bot,
    override val target: Friend,
) : OnlineMessageSource.Outgoing.ToFriend(), MessageSourceInternal, OutgoingMessageSourceInternal {
    object Serializer : MessageSourceSerializerImpl("OnlineMessageSourceToFriend")

    override val bot: Bot
        get() = sender
    override val ids: IntArray
        get() = sequenceIds
    override var isRecalledOrPlanned: AtomicBoolean = AtomicBoolean(false)
    private val jceData: ImMsgBody.SourceMsg by lazy { toJceDataImpl(subject) }
    override fun toJceData(): ImMsgBody.SourceMsg = jceData

    override fun <D, R> accept(visitor: MessageVisitor<D, R>, data: D): R {
        return super<OutgoingMessageSourceInternal>.accept(visitor, data)
    }
}

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(OnlineMessageSourceToStrangerImpl.Serializer::class)
internal class OnlineMessageSourceToStrangerImpl(
    override val sequenceIds: IntArray,
    override val internalIds: IntArray,
    override val time: Int,
    override var originalMessage: MessageChain,
    override val sender: Bot,
    override val target: Stranger,
) : OnlineMessageSource.Outgoing.ToStranger(), MessageSourceInternal, OutgoingMessageSourceInternal {

    constructor(
        delegate: Outgoing,
        target: Stranger,
    ) : this(delegate.ids, delegate.internalIds, delegate.time, delegate.originalMessage, delegate.sender, target)

    object Serializer : MessageSourceSerializerImpl("OnlineMessageSourceToStranger")

    override val bot: Bot
        get() = sender
    override val ids: IntArray
        get() = sequenceIds
    override var isRecalledOrPlanned: AtomicBoolean = AtomicBoolean(false)
    private val jceData: ImMsgBody.SourceMsg by lazy { toJceDataImpl(subject) }
    override fun toJceData(): ImMsgBody.SourceMsg = jceData

    override fun <D, R> accept(visitor: MessageVisitor<D, R>, data: D): R {
        return super<OutgoingMessageSourceInternal>.accept(visitor, data)
    }
}

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(OnlineMessageSourceToTempImpl.Serializer::class)
internal class OnlineMessageSourceToTempImpl(
    override val sequenceIds: IntArray,
    override val internalIds: IntArray,
    override val time: Int,
    override var originalMessage: MessageChain,
    override val sender: Bot,
    override val target: Member,
) : OnlineMessageSource.Outgoing.ToTemp(), MessageSourceInternal, OutgoingMessageSourceInternal {
    constructor(
        delegate: Outgoing,
        target: Member,
    ) : this(delegate.ids, delegate.internalIds, delegate.time, delegate.originalMessage, delegate.sender, target)

    object Serializer : MessageSourceSerializerImpl("OnlineMessageSourceToTemp")

    override val bot: Bot
        get() = sender
    override val ids: IntArray
        get() = sequenceIds
    override var isRecalledOrPlanned: AtomicBoolean = AtomicBoolean(false)
    private val jceData: ImMsgBody.SourceMsg by lazy { toJceDataImpl(subject) }
    override fun toJceData(): ImMsgBody.SourceMsg = jceData

    override fun <D, R> accept(visitor: MessageVisitor<D, R>, data: D): R {
        return super<OutgoingMessageSourceInternal>.accept(visitor, data)
    }
}

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(OnlineMessageSourceToGroupImpl.Serializer::class)
internal class OnlineMessageSourceToGroupImpl(
    coroutineScope: CoroutineScope,
    override val internalIds: IntArray, // aka random
    override val time: Int,
    override var originalMessage: MessageChain,
    override val sender: Bot,
    override val target: Group,
    providedSequenceIds: IntArray? = null,
) : OnlineMessageSource.Outgoing.ToGroup(), MessageSourceInternal, OutgoingMessageSourceInternal {
    object Serializer : MessageSourceSerializerImpl("OnlineMessageSourceToGroup")

    override val ids: IntArray
        get() = sequenceIds
    override val bot: Bot
        get() = sender
    override var isRecalledOrPlanned: AtomicBoolean = AtomicBoolean(false)

    private val sequenceIdDeferred: Deferred<IntArray?> = providedSequenceIds?.let { CompletableDeferred(it) } ?: run {
        val multi = mutableMapOf<Int, Int>()
        coroutineScope.async {
            withTimeoutOrNull(
                timeMillis = 3000L * this@OnlineMessageSourceToGroupImpl.internalIds.size
            ) {
                GlobalEventChannel.parentScope(this)
                    .syncFromEvent<SendGroupMessageReceipt, IntArray>(EventPriority.MONITOR) { receipt ->
                        if (receipt.bot !== bot) return@syncFromEvent null
                        if (receipt.messageRandom in this@OnlineMessageSourceToGroupImpl.internalIds) {
                            multi[receipt.messageRandom] = receipt.sequenceId
                            if (multi.size == this@OnlineMessageSourceToGroupImpl.internalIds.size) {
                                IntArray(multi.size) { index ->
                                    multi[this@OnlineMessageSourceToGroupImpl.internalIds[index]]!!
                                }
                            } else null
                        } else null
                    }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val sequenceIds: IntArray
        get() = when {
            sequenceIdDeferred.isCompleted -> sequenceIdDeferred.getCompleted() ?: intArrayOf()
            !sequenceIdDeferred.isActive -> intArrayOf()
            else -> error("sequenceIds not yet available")
        }


    suspend fun ensureSequenceIdAvailable() = kotlin.run { sequenceIdDeferred.await() }

    private val jceData: ImMsgBody.SourceMsg by lazy {
        val elements = originalMessage.toRichTextElems(subject, withGeneralFlags = true)
        ImMsgBody.SourceMsg(
            origSeqs = sequenceIds,
            senderUin = fromId,
            toUin = target.uin,
            flag = 1,
            elems = elements,
            type = 0,
            time = time,
            pbReserve = SourceMsg.ResvAttr(
                origUids = internalIds.map { it.toLongUnsigned() } // ids is actually messageRandom
            ).toByteArray(SourceMsg.ResvAttr.serializer()),
            srcMsg = MsgComm.Msg(
                msgHead = MsgComm.MsgHead(
                    fromUin = fromId, // qq
                    toUin = target.uin, // group
                    msgType = 82, // 82?
                    c2cCmd = 1,
                    msgSeq = sequenceIds.single(),
                    msgTime = time,
                    msgUid = internalIds.single().toLongUnsigned(),
                    groupInfo = MsgComm.GroupInfo(groupCode = targetId),
                    isSrcMsg = true
                ),
                msgBody = ImMsgBody.MsgBody(
                    richText = ImMsgBody.RichText(
                        elems = elements.toMutableList().also {
                            if (it.last().elemFlags2 == null) it.add(ImMsgBody.Elem(elemFlags2 = ImMsgBody.ElemFlags2()))
                        }
                    )
                )
            ).toByteArray(MsgComm.Msg.serializer())
        )
    }

    override fun toJceData(): ImMsgBody.SourceMsg = jceData

    override fun <D, R> accept(visitor: MessageVisitor<D, R>, data: D): R {
        return super<OutgoingMessageSourceInternal>.accept(visitor, data)
    }
}