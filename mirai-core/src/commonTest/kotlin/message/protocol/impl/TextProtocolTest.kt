/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.internal.message.protocol.impl

import net.mamoe.mirai.contact.MemberPermission
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.AtAll
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.hexToBytes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TextProtocolTest : AbstractMessageProtocolTest() {

    override val protocol = TextProtocol()

    @BeforeEach
    fun `init group`() {
        defaultTarget = bot.addGroup(123, 1230003).apply {
            addMember(1230003, "user3", MemberPermission.OWNER)
        }
    }

    @Test
    fun `test PlainText`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    text = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Text(
                        str = "hello",
                    ),
                )
            )
            message(PlainText("hello"))
        }.doBothChecks()
    }

    @Test
    fun `test AtAll`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    text = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Text(
                        str = "@全体成员",
                        attr6Buf = "00 01 00 00 00 05 01 00 00 00 00 00 00".hexToBytes(),
                    ),
                )
            )
            message(AtAll)
        }.doBothChecks()
    }

    @Test
    fun `AtAll auto append spaces`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    text = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Text(
                        str = "@全体成员",
                        attr6Buf = "00 01 00 00 00 05 01 00 00 00 00 00 00".hexToBytes(),
                    ),
                ),
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    text = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Text(
                        str = "Hi",
                    ),
                ),
            )
            message(AtAll, PlainText("Hi"))
        }.doEncoderChecks()
    }

    @Test
    fun `test At`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    text = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Text(
                        str = "@user3",
                        attr6Buf = "00 01 00 00 00 06 00 00 12 C4 B3 00 00".hexToBytes(),
                    ),
                )
            )
            message(At(1230003))
        }.doBothChecks()
    }

    @Test
    fun `At auto append spaces`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    text = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Text(
                        str = "@user3",
                        attr6Buf = "00 01 00 00 00 06 00 00 12 C4 B3 00 00".hexToBytes(),
                    ),
                ),
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    text = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Text(
                        str = " ",
                    ),
                ),
            )
            message(At(1230003))
            message(PlainText(" "))
            target(bot.addGroup(123, 1230003).apply {
                addMember(1230003, "user3", MemberPermission.OWNER)
            })
        }.doBothChecks()
    }
}