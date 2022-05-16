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
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.ImageType
import net.mamoe.mirai.utils.hexToBytes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ImageProtocolTest : AbstractMessageProtocolTest() {

    override val protocol = ImageProtocol()

    @BeforeEach
    fun `init group`() {
        defaultTarget = bot.addGroup(123, 1230003).apply {
            addMember(1230003, "user3", MemberPermission.OWNER)
        }
    }

    // TODO: 2022/5/16 test for receiving from Windows, Android, iOS clients :)

    ///////////////////////////////////////////////////////////////////////////
    // receive from macOS client
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `group Image receive from macOS client`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    customFace = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.CustomFace(
                        filePath = "{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg",
                        fileId = -1866484636,
                        useful = 1,
                        picMd5 = "A7 CB B5 29 43 A2 12 7C E4 26 59 D2 9B AA 85 15".hexToBytes(),
                        thumbUrl = "/gchatpic_new/123456/12345678-2428482660-A7CBB52943A2127CE42659D29BAA8515/198?term=2",
                        origUrl = "/gchatpic_new/123456/12345678-2428482660-A7CBB52943A2127CE42659D29BAA8515/0?term=2",
                        width = 904,
                        height = 1214,
                        size = 170426,
                        thumbWidth = 147,
                        thumbHeight = 198,
                        _400Url = "/gchatpic_new/123456/12345678-2428482660-A7CBB52943A2127CE42659D29BAA8515/400?term=2",
                        _400Width = 285,
                        _400Height = 384,
                    ),
                )
            )
            message(Image("{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg") {
                width = 904
                height = 1214
                size = 170426
                type = ImageType.JPG
                isEmoji = false
            })
        }.doDecoderChecks()
    }

    @Test
    fun `friend Image receive from macOS client`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    notOnlineImage = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.NotOnlineImage(
                        filePath = "{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg",
                        fileLen = 170426,
                        downloadPath = "/123456-306012740-A7CBB52943A2127CE42659D29BAA8515",
                        oldVerSendFile = "16 20 31 32 32 31 30 31 31 31 31 41 42 20 20 20 20 31 37 30 34 32 36 6B 7B 41 37 43 42 42 35 32 39 2D 34 33 41 32 2D 31 32 37 43 2D 45 34 32 36 2D 35 39 44 32 39 42 41 41 38 35 31 35 7D 2E 6A 70 67 77 2F 31 30 34 30 34 30 30 32 39 30 2D 33 30 36 30 31 32 37 34 30 2D 41 37 43 42 42 35 32 39 34 33 41 32 31 32 37 43 45 34 32 36 35 39 44 32 39 42 41 41 38 35 31 35 41".hexToBytes(),
                        picMd5 = "A7 CB B5 29 43 A2 12 7C E4 26 59 D2 9B AA 85 15".hexToBytes(),
                        picHeight = 1214,
                        picWidth = 904,
                        resId = "/123456-306012740-A7CBB52943A2127CE42659D29BAA8515",
                        thumbUrl = "/offpic_new/123456//123456-306012740-A7CBB52943A2127CE42659D29BAA8515/198?term=2",
                        origUrl = "/offpic_new/123456//123456-306012740-A7CBB52943A2127CE42659D29BAA8515/0?term=2",
                        thumbWidth = 147,
                        thumbHeight = 198,
                        _400Url = "/offpic_new/123456//123456-306012740-A7CBB52943A2127CE42659D29BAA8515/400?term=2",
                        _400Width = 285,
                        _400Height = 384,
                    ),
                )
            )
            message(Image("{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg") {
                width = 904
                height = 1214
                size = 170426
                type = ImageType.JPG
                isEmoji = false
            })
        }.doDecoderChecks()
    }

    ///////////////////////////////////////////////////////////////////////////
    // send without dimension
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `group Image send without dimension`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    customFace = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.CustomFace(
                        filePath = "{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg",
                        flag = byteArrayOf(0, 0, 0, 0),
                        fileType = 66,
                        useful = 1,
                        picMd5 = "A7 CB B5 29 43 A2 12 7C E4 26 59 D2 9B AA 85 15".hexToBytes(),
                        bizType = 5,
                        imageType = 1000,
                        width = 1,
                        height = 1,
                        origin = 1,
                    ),
                )
            )
            message(Image("{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg"))
            targetGroup()
        }.doEncoderChecks()
    }

    @Test
    fun `friend Image send without dimension`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    notOnlineImage = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.NotOnlineImage(
                        filePath = "{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg",
                        downloadPath = "/000000000-000000000-A7CBB52943A2127CE42659D29BAA8515",
                        imgType = 1000,
                        picMd5 = "A7 CB B5 29 43 A2 12 7C E4 26 59 D2 9B AA 85 15".hexToBytes(),
                        picHeight = 1,
                        picWidth = 1,
                        resId = "/000000000-000000000-A7CBB52943A2127CE42659D29BAA8515",
                        original = 1,
                        bizType = 5,
                        pbReserve = "x".toByteArray(), /* 78 02 */
                    ),
                )
            )
            message(Image("{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg"))
            targetFriend()
        }.doEncoderChecks()
    }

    ///////////////////////////////////////////////////////////////////////////
    // send with dimension
    ///////////////////////////////////////////////////////////////////////////


    @Test
    fun `group Image send with dimension`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    customFace = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.CustomFace(
                        filePath = "{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg",
                        flag = byteArrayOf(0, 0, 0, 0),
                        fileType = 66,
                        useful = 1,
                        picMd5 = "A7 CB B5 29 43 A2 12 7C E4 26 59 D2 9B AA 85 15".hexToBytes(),
                        bizType = 5,
                        imageType = 1000,
                        width = 904,
                        height = 1214,
                        size = 170426,
                        origin = 1,
                    ),
                )
            )
            message(Image("{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg") {
                width = 904
                height = 1214
                size = 170426
                type = ImageType.JPG
                isEmoji = false
            })
            targetGroup()
        }.doEncoderChecks()
    }

    private fun ChecksBuilder.targetGroup() {
        target(bot.addGroup(1, 1))
    }

    @Test
    fun `friend Image send with dimension`() {
        buildChecks {
            elem(
                net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.Elem(
                    notOnlineImage = net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody.NotOnlineImage(
                        filePath = "{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg",
                        fileLen = 170426,
                        downloadPath = "/000000000-000000000-A7CBB52943A2127CE42659D29BAA8515",
                        imgType = 1000,
                        picMd5 = "A7 CB B5 29 43 A2 12 7C E4 26 59 D2 9B AA 85 15".hexToBytes(),
                        picHeight = 1214,
                        picWidth = 904,
                        resId = "/000000000-000000000-A7CBB52943A2127CE42659D29BAA8515",
                        original = 1,
                        bizType = 5,
                        pbReserve = "x".toByteArray(), /* 78 02 */
                    ),
                )
            )
            message(Image("{A7CBB529-43A2-127C-E426-59D29BAA8515}.jpg") {
                width = 904
                height = 1214
                size = 170426
                type = ImageType.JPG
                isEmoji = false
            })
            targetFriend()
        }.doEncoderChecks()
    }

    private fun ChecksBuilder.targetFriend() {
        target(bot.addFriend(1))
    }


}