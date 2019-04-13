package org.gcm.tcpcopy.server

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.gcm.tcpcopy.forward.BackChannelPool

class FrontHandler : ChannelInboundHandlerAdapter() {

    override fun channelInactive(ctx: ChannelHandlerContext) {
        GlobalScope.launch {
            BackChannelPool.release(ctx!!.channel())
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val buf = msg as ByteBuf
        val content = ByteArray(buf.readableBytes())
        buf.readBytes(content)
        msg.release()

        GlobalScope.launch {
            BackChannelPool.acquire(ctx.channel()).forEach {
                it.backChannel.writeAndFlush(Unpooled.wrappedBuffer(content))
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        ctx?.close()
//        cause?.printStackTrace()
    }

}