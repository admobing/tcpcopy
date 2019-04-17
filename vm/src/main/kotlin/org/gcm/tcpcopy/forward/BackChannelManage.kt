package org.gcm.tcpcopy.forward

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.gcm.tcpcopy.bean.Backend
import kotlin.coroutines.suspendCoroutine

object BackChannelManage {

    private val frontBack = HashMap<Channel, List<PipeChannel>>()
    private val mutex = Mutex()

    private lateinit var backendList: List<Backend>

    private val clientBootStrap by lazy {
        val b = Bootstrap()
        b.group(NioEventLoopGroup())
        b.channel(NioSocketChannel::class.java)
        b.option(ChannelOption.SO_KEEPALIVE, true)
        b.option(ChannelOption.TCP_NODELAY, true)
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
        b.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
            }
        })
        b
    }

    private suspend fun connect(host: String, port: Int): PipeChannel? {
        return suspendCoroutine { co ->
            val future = clientBootStrap.connect(host, port)
            future.addListener {
                if (it.isSuccess) {
                    co.resumeWith(Result.success(PipeChannel(future.channel(), host, port)))
                } else {
                    co.resumeWith(Result.success(null))
                }
            }
        }
    }

    fun setBackendList(backendList: List<Backend>) {
        this.backendList = backendList
    }

    suspend fun acquire(frontChannel: Channel, timeout: Long = 2000L): List<PipeChannel> {
        val aliveChannelList = mutex.withLock {
            frontBack.getOrDefault(frontChannel, emptyList()).filter { !it.disconnect }
        }
        if (aliveChannelList.size == backendList.size) {
            return aliveChannelList
        }

        val acqChannelList = backendList.filter { backend ->
            aliveChannelList.find { it.host.equals(backend.host) && it.port == backend.port } == null
        }.map { backend ->
            withTimeoutOrNull(timeout) {
                connect(backend.host, backend.port)?.apply {
                    this.frontChannel = frontChannel
                }
            }
        }.filter { it != null } as List<PipeChannel>

        if (acqChannelList.isEmpty()) {
            return aliveChannelList
        }

        val ret = ArrayList<PipeChannel>()
        ret.addAll(aliveChannelList)
        ret.addAll(acqChannelList)
        mutex.withLock {
            frontBack.put(frontChannel, ret)
        }
        return ret
    }

    suspend fun release(frontChannel: Channel) {
        mutex.withLock {
            frontBack.remove(frontChannel)?.forEach {
                it.backChannel.close()
            }
        }
    }
}
