package net.postchain.client.request

class Endpoint(val url: String) {
    private var health: EndpointHealthStatus = EndpointHealthStatus.Reachable

    fun isReachable() = health == EndpointHealthStatus.Reachable

    fun setUnreachable() { health = EndpointHealthStatus.Unreachable }

    fun setReachable() { health = EndpointHealthStatus.Reachable }
}
