package org.gcm.tcpcopy.bean

import kotlinx.coroutines.CompletableDeferred
import org.gcm.tcpcopy.forward.PipeChannel

data class Backend(val host: String, val port: Int)

class GetBackend(val host: String, val port: Int, val rsp: CompletableDeferred<List<PipeChannel>>)