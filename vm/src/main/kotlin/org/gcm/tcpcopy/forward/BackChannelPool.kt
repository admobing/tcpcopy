package org.gcm.tcpcopy.forward

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.gcm.tcpcopy.bean.Backend
import kotlin.coroutines.suspendCoroutine

object BackChannelPool {

    private val idleSize = 10

    private val frontBack = HashMap<Channel, List<PipeChannel>>()
    private val backChannelPool = HashMap<Backend, kotlinx.coroutines.channels.Channel<PipeChannel>>()

    private val mutex = Mutex()

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

    fun build(backendList: List<Backend>) {
        backendList.forEach { backend ->
            val channelPool = kotlinx.coroutines.channels.Channel<PipeChannel>(idleSize)
            backChannelPool.put(backend, channelPool)

            GlobalScope.launch {
                while (isActive) {
                    if (channelPool.isFull) {
                        delay(1000)
                        continue
                    }

                    connect(backend.host, backend.port)?.apply {
                        channelPool.send(this)
                    }
                }
            }
        }
    }

    suspend fun acquire(frontChannel: Channel): List<PipeChannel> {
        val existsChanneList = mutex.withLock {
            frontBack.getOrDefault(frontChannel, emptyList())
        }

        val aliveChannelList = existsChanneList.filter { !it.disconnect }
        if (aliveChannelList.size == backChannelPool.size) {
            return aliveChannelList
        }

        val acqChannelList = backChannelPool.filter { en ->
            aliveChannelList.find { it.host.equals(en.key.host) && it.port == en.key.port } == null
        }.map {
            withTimeoutOrNull(2000) {
                it.value.receive()
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
            acqChannelList.forEach {
                it.frontChannel = frontChannel
            }
        }

        return ret
    }

    suspend fun release(frontChannel: Channel) {
        mutex.withLock {
            frontBack.getOrDefault(frontChannel, emptyList()).forEach {
                it.backChannel.close()
            }
            frontBack.remove(frontChannel)
        }
    }
}
