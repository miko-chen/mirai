/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.internal.network.impl.netty

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.mamoe.mirai.internal.network.components.BotOfflineEventMonitor
import net.mamoe.mirai.internal.network.components.BotOfflineEventMonitorImpl
import net.mamoe.mirai.internal.network.components.FirstLoginResult
import net.mamoe.mirai.internal.network.framework.AbstractNettyNHTest
import net.mamoe.mirai.internal.network.framework.TestNettyNH
import net.mamoe.mirai.internal.network.framework.setSsoProcessor
import net.mamoe.mirai.internal.network.handler.NetworkHandler
import net.mamoe.mirai.internal.network.handler.selector.KeepAliveNetworkHandlerSelector
import net.mamoe.mirai.internal.network.handler.selector.SelectorNetworkHandler
import net.mamoe.mirai.internal.network.handler.selectorLogger
import net.mamoe.mirai.internal.network.protocol.packet.login.StatSvc
import net.mamoe.mirai.internal.test.runBlockingUnit
import net.mamoe.mirai.network.CustomLoginFailedException
import net.mamoe.mirai.utils.cast
import org.junit.jupiter.api.AfterEach
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

internal class NettyBotNormalLoginTest : AbstractNettyNHTest() {
    init {
        overrideComponents[BotOfflineEventMonitor] = BotOfflineEventMonitorImpl()
    }

    val selector = KeepAliveNetworkHandlerSelector(selectorLogger) {
        super.factory.create(createContext(), createAddress())
    }

    override val network: TestNettyNH
        get() = bot.network.cast<SelectorNetworkHandler<*>>().selector.getCurrentInstanceOrCreate().cast()

    override fun createHandler(): NetworkHandler {
        return SelectorNetworkHandler(selector)
    }

    class CusLoginException(message: String?) : CustomLoginFailedException(true, message)

    @AfterEach
    fun `close bot`() = runBlockingUnit {
        bot.logger.info("[TEST UNIT] Releasing bot....")
        bot.closeAndJoin()
    }

    @Test
    fun `test login fail`() = runBlockingUnit {
        setSsoProcessor { throw CusLoginException("A") }
        assertFailsWith<CusLoginException>("A") { bot.login() }
        assertFalse(bot.isActive)
    }

    // #1963
    @Test
    fun `test first login failure with internally handled exceptions`() = runBlockingUnit {
        setSsoProcessor { throw IOException("test Connection reset by peer") }
        assertFailsWith<IOException>("test Connection reset by peer") { bot.login() }
        assertState(NetworkHandler.State.CLOSED)
    }

    // #1963
    @Test
    fun `test first login failure with internally handled exceptions2`() = runBlockingUnit {
        setSsoProcessor { throw NettyChannelException("test Connection reset by peer") }
        assertFailsWith<NettyChannelException>("test Connection reset by peer") { bot.login() }
        assertState(NetworkHandler.State.CLOSED)
    }

    // 经过 #1963 考虑后在初次登录遇到任何错误都终止并传递异常
//    @Test
//    fun `test network broken`() = runBlockingUnit {
//        var retryCounter = 0
//        setSsoProcessor {
//            eventDispatcher.joinBroadcast()
//            if (retryCounter++ >= 15) {
//                return@setSsoProcessor
//            }
//            channel.pipeline().fireExceptionCaught(IOException("TestNetworkBroken"))
//            awaitCancellation() // receive exception from "network"
//        }
//        bot.login()
//    }

    @Test
    fun `test resume after MsfOffline received after first login`() = runBlockingUnit {
        bot.login()
        assertEquals(FirstLoginResult.PASSED, firstLoginResult)
        bot.network.close(StatSvc.ReqMSFOffline.MsfOfflineToken(0, 0, 0))

        eventDispatcher.joinBroadcast()
        delay(1000L) // auto resume in BotOfflineEventMonitor
        eventDispatcher.joinBroadcast()

        assertState(NetworkHandler.State.OK)
    }
}
