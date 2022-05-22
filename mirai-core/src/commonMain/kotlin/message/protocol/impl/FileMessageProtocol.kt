/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.internal.message.protocol.impl

import kotlinx.io.core.readUShort
import net.mamoe.mirai.internal.message.data.FileMessageImpl
import net.mamoe.mirai.internal.message.protocol.MessageProtocol
import net.mamoe.mirai.internal.message.protocol.ProcessorCollector
import net.mamoe.mirai.internal.message.protocol.decode.MessageDecoder
import net.mamoe.mirai.internal.message.protocol.decode.MessageDecoderContext
import net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody
import net.mamoe.mirai.internal.network.protocol.data.proto.ObjMsg
import net.mamoe.mirai.internal.utils.io.serialization.readProtoBuf
import net.mamoe.mirai.utils.read

internal class FileMessageProtocol : MessageProtocol() {
    override fun ProcessorCollector.collectProcessorsImpl() {
        // no encoder
        add(Decoder())
    }

    private class Decoder : MessageDecoder {
        override suspend fun MessageDecoderContext.process(data: ImMsgBody.Elem) {
            if (data.transElemInfo == null) return
            if (data.transElemInfo.elemType != 24) return

            markAsConsumed()

            data.transElemInfo.elemValue.read {
                // group file feed
                // 01 00 77 08 06 12 0A 61 61 61 61 61 61 2E 74 78 74 1A 06 31 35 42 79 74 65 3A 5F 12 5D 08 66 12 25 2F 64 37 34 62 62 66 33 61 2D 37 62 32 35 2D 31 31 65 62 2D 38 34 66 38 2D 35 34 35 32 30 30 37 62 35 64 39 66 18 0F 22 0A 61 61 61 61 61 61 2E 74 78 74 28 00 3A 00 42 20 61 33 32 35 66 36 33 34 33 30 65 37 61 30 31 31 66 37 64 30 38 37 66 63 33 32 34 37 35 34 39 63
                //                fun getFileRsrvAttr(file: ObjMsg.MsgContentInfo.MsgFile): HummerResv21.ResvAttr? {
                //                    if (file.ext.isEmpty()) return null
                //                    val element = kotlin.runCatching {
                //                        jsonForFileDecode.parseToJsonElement(file.ext) as? JsonObject
                //                    }.getOrNull() ?: return null
                //                    val extInfo = element["ExtInfo"]?.toString()?.decodeBase64() ?: return null
                //                    return extInfo.loadAs(HummerResv21.ResvAttr.serializer())
                //                }

                val var7 = readByte()
                if (var7 == 1.toByte()) {
                    while (remaining > 2) {
                        val proto = readProtoBuf(ObjMsg.ObjMsg.serializer(), readUShort().toInt())
                        // proto.msgType=6

                        val file = proto.msgContentInfo.firstOrNull()?.msgFile ?: continue // officially get(0) only.
                        //                        val attr = getFileRsrvAttr(file) ?: continue
                        //                        val info = attr.forwardExtFileInfo ?: continue

                        collect(
                            FileMessageImpl(
                                id = file.filePath,
                                busId = file.busId, // path i.e. /a99e95fa-7b2d-11eb-adae-5452007b698a
                                name = file.fileName,
                                size = file.fileSize
                            )
                        )
                    }
                }
            }
        }
    }
}