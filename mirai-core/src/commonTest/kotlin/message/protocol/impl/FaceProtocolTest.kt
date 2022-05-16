/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.internal.message.protocol.impl

import net.mamoe.mirai.internal.message.protocol.MessageProtocol
import net.mamoe.mirai.message.data.Face
import net.mamoe.mirai.message.data.MessageSourceKind
import net.mamoe.mirai.message.data.messageChainOf
import net.mamoe.mirai.utils.hexToBytes
import org.junit.jupiter.api.Test

internal class FaceProtocolTest : AbstractMessageProtocolTest() {
    override val protocol: MessageProtocol = FaceProtocol()

    @Test
    fun `can encode`() {
        doEncoderChecks(
            net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                face = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Face(
                    index = 1,
                    old = "14 42".hexToBytes(),
                    buf = "00 01 00 04 52 CC F5 D0".hexToBytes(),
                ),
            ),
        ) {
            encode(
                messageChainOf(Face(Face.PIE_ZUI)),
                messageTarget = null, withGeneralFlags = true, isForward = false
            )
        }
    }

    @Test
    fun `can decode`() {
        doDecoderChecks(
            messageChainOf(Face(Face.YIN_XIAN)),
        ) {
            decode(
                listOf(
                    net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                        face = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Face(
                            index = 108,
                            old = "14 AD".hexToBytes(),
                        ),
                    )
                ),
                groupIdOrZero = 0,
                MessageSourceKind.GROUP,
                bot,
            )
        }

    }

}