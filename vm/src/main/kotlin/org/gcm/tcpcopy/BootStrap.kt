@file:JvmName("BootStrap")

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import org.gcm.tcpcopy.bean.Backend
import org.gcm.tcpcopy.forward.BackChannelPool
import org.gcm.tcpcopy.server.Server

class Args(parser: ArgParser) {
    val port by parser.storing("-p", "--port", help = "监听端口") { toInt() }.default(7001)
    val server by parser.storing("-b", "--back", help = "后端地址,多个地址间使用分号间隔.例:192.168.10.1:4444;192.168.10.2:4444") { split(";") }
}

fun main(args: Array<String>) = mainBody("tcpcopy") {
    Args(ArgParser(args)).run {
        val backendList = try {
            server.map {
                val hostPort = it.split(":")
                Backend(hostPort[0], hostPort[1].toInt())
            }
        } catch (err: Throwable) {
            println("后端地址格式不正确")
            return@run
        }

        println(backendList)

        BackChannelPool.build(backendList)
        Server(port).start()
    }
}
