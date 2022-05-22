/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.internal.message.protocol

import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.ContactOrBot
import net.mamoe.mirai.internal.contact.AbstractContact
import net.mamoe.mirai.internal.contact.SendMessageStep
import net.mamoe.mirai.internal.contact.impl
import net.mamoe.mirai.internal.message.DeepMessageRefiner.refineDeep
import net.mamoe.mirai.internal.message.EmptyRefineContext
import net.mamoe.mirai.internal.message.LightMessageRefiner.refineLight
import net.mamoe.mirai.internal.message.RefineContext
import net.mamoe.mirai.internal.message.contextualBugReportException
import net.mamoe.mirai.internal.message.protocol.decode.*
import net.mamoe.mirai.internal.message.protocol.encode.*
import net.mamoe.mirai.internal.message.protocol.outgoing.*
import net.mamoe.mirai.internal.network.component.ComponentStorage
import net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody
import net.mamoe.mirai.internal.utils.runCoroutineInPlace
import net.mamoe.mirai.internal.utils.structureToString
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.visitor.RecursiveMessageVisitor
import net.mamoe.mirai.message.data.visitor.accept
import net.mamoe.mirai.utils.MutableTypeSafeMap
import net.mamoe.mirai.utils.buildTypeSafeMap
import net.mamoe.mirai.utils.castUp
import java.util.*
import kotlin.reflect.KClass

internal interface MessageProtocolFacade {
    val encoderPipeline: MessageEncoderPipeline
    val decoderPipeline: MessageDecoderPipeline
    val preprocessorPipeline: OutgoingMessagePipeline
    val outgoingPipeline: OutgoingMessagePipeline

    val loaded: List<MessageProtocol>

    /**
     * Encode high-level [MessageChain] to give list of low-level and protocol-specific [ImMsgBody.Elem]s.
     */
    fun encode(
        chain: MessageChain,
        messageTarget: ContactOrBot?, // for At.display, QuoteReply, Image, and more.
        withGeneralFlags: Boolean, // important for RichMessages, may also be helpful for others
        isForward: Boolean = false, // is inside forward, for At.display
    ): List<ImMsgBody.Elem>

    /**
     * Decode list of low-level and protocol-specific [ImMsgBody.Elem]s to give a high-level [MessageChain].
     *
     * [SingleMessage]s are appended to the [builder].
     */
    fun decode(
        elements: List<ImMsgBody.Elem>,
        groupIdOrZero: Long,
        messageSourceKind: MessageSourceKind,
        bot: Bot,
        builder: MessageChainBuilder,
    )


    /**
     * Pre-process a message
     * @see OutgoingMessagePreprocessor
     */
    suspend fun <C : AbstractContact> preprocess(
        target: C,
        message: Message,
        components: ComponentStorage,
    ): MessageChain

    /**
     * Send a message
     * @see OutgoingMessageProcessor
     */
    suspend fun <C : AbstractContact> sendOutgoing(
        target: C,
        message: Message,
        components: ComponentStorage,
    ): MessageReceipt<C>

    /**
     * Preprocess and send a message
     * @see OutgoingMessagePreprocessor
     * @see OutgoingMessageProcessor
     */
    suspend fun <C : AbstractContact> preprocessAndSendOutgoing(
        target: C,
        message: Message,
        components: ComponentStorage,
    ): MessageReceipt<C>

    /**
     * Decode list of low-level and protocol-specific [ImMsgBody.Elem]s to give a high-level [MessageChain].
     */
    fun decode(
        elements: List<ImMsgBody.Elem>,
        groupIdOrZero: Long,
        messageSourceKind: MessageSourceKind,
        bot: Bot,
    ): MessageChain = buildMessageChain { decode(elements, groupIdOrZero, messageSourceKind, bot, this) }

    fun copy(): MessageProtocolFacade

    /**
     * The default global instance.
     */
    companion object INSTANCE : MessageProtocolFacade by MessageProtocolFacadeImpl()
}

internal fun MessageProtocolFacade.decodeAndRefineLight(
    elements: List<ImMsgBody.Elem>,
    groupIdOrZero: Long,
    messageSourceKind: MessageSourceKind,
    bot: Bot,
    refineContext: RefineContext = EmptyRefineContext
): MessageChain = decode(elements, groupIdOrZero, messageSourceKind, bot).refineLight(bot, refineContext)

internal suspend fun MessageProtocolFacade.decodeAndRefineDeep(
    elements: List<ImMsgBody.Elem>,
    groupIdOrZero: Long,
    messageSourceKind: MessageSourceKind,
    bot: Bot,
    refineContext: RefineContext = EmptyRefineContext
): MessageChain = decode(elements, groupIdOrZero, messageSourceKind, bot).refineDeep(bot, refineContext)


internal class MessageProtocolFacadeImpl(
    private val protocols: Iterable<MessageProtocol> = ServiceLoader.load(MessageProtocol::class.java)
) : MessageProtocolFacade {
    override val encoderPipeline: MessageEncoderPipeline = MessageEncoderPipelineImpl()
    override val decoderPipeline: MessageDecoderPipeline = MessageDecoderPipelineImpl()
    override val preprocessorPipeline: OutgoingMessagePipeline = OutgoingMessagePipelineImpl()
    override val outgoingPipeline: OutgoingMessagePipeline = OutgoingMessagePipelineImpl()

    override val loaded: List<MessageProtocol> = kotlin.run {
        val instances: PriorityQueue<MessageProtocol> = protocols
            .toCollection(PriorityQueue(MessageProtocol.PriorityComparator.reversed()))
        for (instance in instances) {
            instance.collectProcessors(object : ProcessorCollector() {
                override fun <T : SingleMessage> add(encoder: MessageEncoder<T>, elementType: KClass<T>) {
                    this@MessageProtocolFacadeImpl.encoderPipeline.registerProcessor(
                        MessageEncoderProcessor(
                            encoder,
                            elementType
                        )
                    )
                }

                override fun add(decoder: MessageDecoder) {
                    this@MessageProtocolFacadeImpl.decoderPipeline.registerProcessor(MessageDecoderProcessor(decoder))
                }

                override fun add(preprocessor: OutgoingMessagePreprocessor) {
                    preprocessorPipeline.registerProcessor(OutgoingMessageProcessorAdapter(preprocessor))
                }

                override fun add(transformer: OutgoingMessageTransformer) {
                    outgoingPipeline.registerProcessor(OutgoingMessageProcessorAdapter(transformer))
                }

                override fun add(sender: OutgoingMessageSender) {
                    outgoingPipeline.registerProcessor(OutgoingMessageProcessorAdapter(sender))
                }

                override fun add(postprocessor: OutgoingMessagePostprocessor) {
                    outgoingPipeline.registerProcessor(OutgoingMessageProcessorAdapter(postprocessor))
                }
            })
        }
        instances.toList()
    }

    override fun encode(
        chain: MessageChain,
        messageTarget: ContactOrBot?,
        withGeneralFlags: Boolean,
        isForward: Boolean
    ): List<ImMsgBody.Elem> {
        val pipeline = encoderPipeline

        val attributes = buildTypeSafeMap {
            set(MessageEncoderContext.CONTACT, messageTarget)
            set(MessageEncoderContext.ORIGINAL_MESSAGE, chain)
            set(MessageEncoderContext.ADD_GENERAL_FLAGS, withGeneralFlags)
            set(MessageEncoderContext.IS_FORWARD, isForward)
        }

        val builder = ArrayList<ImMsgBody.Elem>(chain.size)

        chain.accept(object : RecursiveMessageVisitor<Unit>() {
            override fun visitSingleMessage(message: SingleMessage, data: Unit) {
                runCoroutineInPlace {
                    builder.addAll(pipeline.process(message, attributes).collected)
                }
            }
        })

        return builder
    }

    override fun decode(
        elements: List<ImMsgBody.Elem>,
        groupIdOrZero: Long,
        messageSourceKind: MessageSourceKind,
        bot: Bot,
        builder: MessageChainBuilder
    ) {
        val pipeline = decoderPipeline

        val attributes = buildTypeSafeMap {
            set(MessageDecoderContext.BOT, bot)
            set(MessageDecoderContext.MESSAGE_SOURCE_KIND, messageSourceKind)
            set(MessageDecoderContext.GROUP_ID, groupIdOrZero)
        }

        runCoroutineInPlace {
            elements.forEach { builder.addAll(pipeline.process(it, attributes).collected) }
        }
    }

    override suspend fun <C : AbstractContact> preprocess(
        target: C,
        message: Message,
        components: ComponentStorage
    ): MessageChain {
        val attributes = createAttributesForOutgoingMessage(target, message, components)

        return preprocessorPipeline.process(message.toMessageChain(), attributes).context.currentMessageChain
    }

    override suspend fun <C : AbstractContact> sendOutgoing(
        target: C, message: Message,
        components: ComponentStorage
    ): MessageReceipt<C> {
        val attributes = createAttributesForOutgoingMessage(target, message, components)

        val (_, result) = outgoingPipeline.process(message.toMessageChain(), attributes)

        return getSingleReceipt(result, message)
    }

    override suspend fun <C : AbstractContact> preprocessAndSendOutgoing(
        target: C,
        message: Message,
        components: ComponentStorage
    ): MessageReceipt<C> {
        val attributes = createAttributesForOutgoingMessage(target, message, components)

        val (context, _) = preprocessorPipeline.process(message.toMessageChain(), attributes)
        val (_, result) = outgoingPipeline.process(message.toMessageChain(), context, attributes)

        return getSingleReceipt(result, message)
    }

    override fun copy(): MessageProtocolFacade {
        return MessageProtocolFacadeImpl(protocols)
    }

    private fun <C : AbstractContact> getSingleReceipt(
        result: Collection<MessageReceipt<*>>,
        message: Message
    ): MessageReceipt<C> {
        when (result.size) {
            0 -> throw contextualBugReportException(
                "Internal error: no MessageReceipt was returned from OutgoingMessagePipeline for message",
                forDebug = message.structureToString()
            )
            1 -> return result.single().castUp()
            else -> throw contextualBugReportException(
                "Internal error: multiple MessageReceipts were returned from OutgoingMessagePipeline: $result",
                forDebug = message.structureToString()
            )
        }
    }

    private fun <C : AbstractContact> createAttributesForOutgoingMessage(
        target: C,
        message: Message,
        context: ComponentStorage
    ): MutableTypeSafeMap {
        val attributes = buildTypeSafeMap {
            set(OutgoingMessagePipelineContext.CONTACT, target.impl())
            set(OutgoingMessagePipelineContext.ORIGINAL_MESSAGE, message)
            set(OutgoingMessagePipelineContext.STEP, SendMessageStep.FIRST)
            set(OutgoingMessagePipelineContext.PROTOCOL_STRATEGY, context[MessageProtocolStrategy].castUp())
            set(OutgoingMessagePipelineContext.HIGHWAY_UPLOADER, context[HighwayUploader])
        }
        return attributes
    }
}
