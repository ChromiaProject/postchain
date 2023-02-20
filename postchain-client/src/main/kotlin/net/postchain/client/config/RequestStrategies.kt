package net.postchain.client.config

import net.postchain.client.impl.AbortOnErrorRequestStrategyFactory
import net.postchain.client.impl.QueryMajorityRequestStrategyFactory
import net.postchain.client.impl.SingleEndpointRequestStrategyFactory
import net.postchain.client.impl.TryNextOnErrorRequestStrategyFactory
import net.postchain.client.request.RequestStrategyFactory

enum class RequestStrategies(val factory: RequestStrategyFactory) {
    SINGLE(SingleEndpointRequestStrategyFactory()),
    ABORT_ON_ERROR(AbortOnErrorRequestStrategyFactory()),
    TRY_NEXT_ON_ERROR(TryNextOnErrorRequestStrategyFactory()),
    QUERY_MAJORITY(QueryMajorityRequestStrategyFactory())
}
