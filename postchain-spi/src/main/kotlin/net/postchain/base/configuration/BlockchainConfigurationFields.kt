package net.postchain.base.configuration

import net.postchain.core.BlockchainConfiguration

const val KEY_BLOCKSTRATEGY = "blockstrategy"
const val KEY_BLOCKSTRATEGY_NAME = "name"
const val KEY_BLOCKSTRATEGY_MAXBLOCKSIZE = "maxblocksize"
const val KEY_BLOCKSTRATEGY_MAXBLOCKTRANSACTIONS = "maxblocktransactions"
const val KEY_BLOCKSTRATEGY_MININTERBLOCKINTERVAL = "mininterblockinterval"
const val KEY_BLOCKSTRATEGY_MAXBLOCKTIME = "maxblocktime"
const val KEY_BLOCKSTRATEGY_MAXTXDELAY = "maxtxdelay"
const val KEY_BLOCKSTRATEGY_MINBACKOFFTIME = "minbackofftime"
const val KEY_BLOCKSTRATEGY_MAXBACKOFFTIME = "maxbackofftime"
const val KEY_BLOCKSTRATEGY_MAXSPECIALENDTRANSACTIONSIZE = "maxspecialendtransactionsize"
const val KEY_BLOCKSTRATEGY_PREEMPTIVEBLOCKBUILDING = "preemptiveblockbuilding"

const val KEY_MAX_BLOCK_FUTURE_TIME = "max_block_future_time"

const val KEY_ADD_PRIMARY_KEY_TO_HEADER = "add_primary_key_to_header"

const val KEY_QUEUE_CAPACITY = "txqueuecapacity"
const val KEY_QUEUE_TX_RECHECK_INTERVAL = "txqueuerecheckinterval"

const val KEY_CONFIGURATIONFACTORY = "configurationfactory"

const val KEY_SIGNERS = "signers"

const val KEY_GTX = "gtx"
const val KEY_GTX_MODULES = "modules"
const val KEY_GTX_TX_SIZE = "max_transaction_size"
const val KEY_GTX_MAX_TX_SIGNATURES = "max_transaction_signatures"
const val KEY_GTX_ALLOWOVERRIDES = "allowoverrides"

const val KEY_DEPENDENCIES = "dependencies"

const val KEY_HISTORIC_BRID = "historic_brid"

const val KEY_SYNC = "sync"
const val KEY_SYNC_EXT = "sync_ext"

const val KEY_REVOLT = "revolt"
const val KEY_REVOLT_TIMEOUT = "timeout"
const val KEY_REVOLT_EXPONENTIAL_DELAY_INITIAL = "exponential_delay_initial"
const val KEY_REVOLT_EXPONENTIAL_DELAY_BASE = "exponential_delay_base"
const val KEY_REVOLT_EXPONENTIAL_DELAY_POWER_BASE = "exponential_delay_power_base"
const val KEY_REVOLT_EXPONENTIAL_DELAY_MAX = "exponential_delay_max"
const val KEY_REVOLT_FAST_REVOLT_STATUS_TIMEOUT = "fast_revolt_status_timeout"
const val KEY_REVOLT_WHEN_SHOULD_BUILD_BLOCK = "revolt_when_should_build_block"

const val KEY_CONFIG_CONSENSUS_STRATEGY = "config_consensus_strategy"

const val KEY_QUERY_CACHE_TTL_SECONDS = "query_cache_ttl_seconds"
const val KEY_ASYNC_QUERY_QUEUE_CAPACITY = "async_query_queue_capacity"
const val KEY_ASYNC_QUERY_TIMEOUT_SECONDS = "async_query_timeout_seconds"
const val KEY_ASYNC_QUERY_RESULT_RETENTION_SECONDS = "async_query_result_retention_seconds"

const val KEY_FEATURES = "features"

val BlockchainConfiguration.queryCacheTtlSeconds: Long
    get() = this.rawConfig[KEY_QUERY_CACHE_TTL_SECONDS]?.asInteger() ?: 0L

val BlockchainConfiguration.asyncQueryQueueCapacity: Long
    get() = this.rawConfig[KEY_ASYNC_QUERY_QUEUE_CAPACITY]?.asInteger() ?: 0L

val BlockchainConfiguration.asyncQueryTimeoutSeconds: Long
    get() = this.rawConfig[KEY_ASYNC_QUERY_TIMEOUT_SECONDS]?.asInteger() ?: (60 * 60) // 1 hour

val BlockchainConfiguration.asyncQueryResultRetentionSeconds: Long
    get() = this.rawConfig[KEY_ASYNC_QUERY_RESULT_RETENTION_SECONDS]?.asInteger() ?: (60 * 60) // 1 hour
