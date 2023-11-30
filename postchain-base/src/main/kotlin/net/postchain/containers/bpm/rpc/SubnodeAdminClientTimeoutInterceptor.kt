package net.postchain.containers.bpm.rpc

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.MethodDescriptor
import net.postchain.containers.infra.ContainerNodeConfig
import java.util.concurrent.TimeUnit

class SubnodeAdminClientTimeoutInterceptor(private val containerNodeConfig: ContainerNodeConfig) : ClientInterceptor {
    override fun <ReqT : Any?, RespT : Any?> interceptCall(method: MethodDescriptor<ReqT, RespT>, callOptions: CallOptions, next: Channel): ClientCall<ReqT, RespT> {
        val deadLineCallOptions = callOptions.withDeadlineAfter(containerNodeConfig.adminClientTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        return next.newCall(method, deadLineCallOptions)
    }
}
