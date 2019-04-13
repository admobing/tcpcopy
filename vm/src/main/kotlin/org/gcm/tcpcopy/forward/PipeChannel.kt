package org.gcm.tcpcopy.forward

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

class PipeChannel(val backChannel: Channel, val host: String, val port: Int, @Volatile var disconnect: Boolean = false) {
    var frontChannel: Channel? = null

    init {
        backChannel.pipeline().addFirst(object : ChannelInboundHandlerAdapter() {
            override fun channelInactive(ctx: ChannelHandlerContext?) {
                disconnect = true
            }


            override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                frontChannel?.writeAndFlush(msg)
            }

            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
                ctx.close()
//                cause?.printStackTrace()
            }
        })
    }
}