/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.internal.message.protocol.impl

import net.mamoe.mirai.internal.message.protocol.*
import net.mamoe.mirai.internal.network.protocol.data.proto.HummerCommelem
import net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody
import net.mamoe.mirai.internal.utils.io.serialization.loadAs
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.VipFace

internal class VipFaceProtocol : MessageProtocol() {
    override fun ProcessorCollector.collectProcessorsImpl() {
        add(Encoder())
        add(Decoder())
    }

    private class Encoder : MessageEncoder<VipFace> {
        override suspend fun MessageEncoderContext.process(data: VipFace) {
            markAsConsumed()
            processAlso(PlainText(data.contentToString()))
        }
    }

    private class Decoder : MessageDecoder {
        override suspend fun MessageDecoderContext.process(data: ImMsgBody.Elem) {
            if (data.commonElem == null) return
            if (data.commonElem.serviceType != 23) return
            markAsConsumed()

            val proto =
                data.commonElem.pbElem.loadAs(HummerCommelem.MsgElemInfoServtype23.serializer())
            collect(VipFace(VipFace.Kind(proto.faceType, proto.faceSummary), proto.faceBubbleCount))
        }
    }
}