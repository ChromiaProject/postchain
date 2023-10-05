Below is an attempt to document all node properties that can be configured via `properties` files. Most properties can be set via environment variables. For a list of blockchain configuration properties see this [page](Blockchain-Configuration-Properties.md).

| Name | Description | Type | Default | Environment Variable |
| ------ | ------ | ------ | ------ | ------ |
| `cryptosystem` | Class name of cryptosystem to use | String | "net.postchain.crypto.Secp256K1CryptoSystem" | `POSTCHAIN_CRYPTO_SYSTEM` |
| `configuration.provider.node` | properties / manual / managed. | String | "properties" | `POSTCHAIN_NODE_CONFIG_PROVIDER` |
| `database.driverclass` | Database driver class. | String | "org.postgresql.Driver" | `POSTCHAIN_DB_DRIVER`|
| `database.url` | Database URL. | String | "" | `POSTCHAIN_DB_URL` |
| `database.schema` | Database schema. | String | "" | `POSTCHAIN_DB_SCHEMA` |
| `database.username` | Database username. | String | "" | `POSTCHAIN_DB_USERNAME` |
| `database.password` | Database password. | String | "" | `POSTCHAIN_DB_PASSWORD` |
| `database.readConcurrency` | Database read concurrency. | Int | 10 | `POSTCHAIN_DB_READ_CONCURRENCY` |
| `database.blockBuilderWriteConcurrency` | Database block builder connection pool write concurrency. | Int | 8 | `POSTCHAIN_DB_BLOCK_BUILDER_WRITE_CONCURRENCY` |
| `database.sharedWriteConcurrency` | Database shared connection pool write concurrency. | Int | 2 | `POSTCHAIN_DB_SHARED_WRITE_CONCURRENCY` |
| `database.blockBuilderMaxWaitWrite` | Maximum wait time for a connection on the block builder connection pool (milliseconds). | Int | 100 | `POSTCHAIN_DB_BLOCK_BUILDER_MAX_WAIT_WRITE` |
| `database.sharedMaxWaitWrite` | Maximum wait time for a connection on the shared connection pool (milliseconds). | Int | 10 000 | `POSTCHAIN_DB_SHARED_MAX_WAIT_WRITE` |
| `database.suppressCollationCheck` | Ignore incorrect database collation configuration | Boolean | false | `POSTCHAIN_DB_SUPPRESS_COLLATION_CHECK` |
| `exit-on-fatal-error` | Exit process upon fatal database error | Boolean | false | `POSTCHAIN_EXIT_ON_FATAL_ERROR` |
| `infrastructure` | The infrastructure implementation to use. | String | "base/ebft" | `POSTCHAIN_INFRASTRUCTURE` |
| `genesis.pubkey` | Public key of genesis node | String | | `POSTCHAIN_GENESIS_PUBKEY` |
| `genesis.host` | Host address of genesis node | String | | `POSTCHAIN_GENESIS_HOST` |
| `genesis.port` | Peer-to-peer port of genesis node | String | | `POSTCHAIN_GENESIS_PORT` |
| `messaging.privkey` | Private key of the node | String | "" | `POSTCHAIN_PRIVKEY` |
| `messaging.pubkey` | Public key of the node | String | "" | `POSTCHAIN_PUBKEY` |
| `messaging.port` | Peer-to-peer port | Int | 9870 | `POSTCHAIN_PORT` |
| `metrics.prometheus.port` | Port to expose Prometheus metrics on, or -1 to disable | Int | -1 | `POSTCHAIN_PROMETHEUS_PORT` |
| `fastsync.resurrect_drained_time` | Time (ms) until a DRAINED node is set to SYNCABLE again. | Long | 10000 | `POSTCHAIN_FASTSYNC_RESURRECT_DRAINED_TIME` |
| `fastsync.resurrect_unresponsive_time` | Time (ms) until a UNRESPONSIVE node is set to SYNCABLE again. | Long | 20000 | `POSTCHAIN_FASTSYNC_RESURRECT_UNRESPONSIVE_TIME` |
| `fastsync.parallelism` | Number of parallel sync jobs. | Int | 10 | `POSTCHAIN_FASTSYNC_PARALLELISM` |
| `fastsync.exit_delay` | Do not exit sync for at least this amount of time (ms). | Long | 60000 | `POSTCHAIN_FASTSYNC_EXIT_DELAY` |
| `fastsync.job_timeout` | Sync job timeout (ms). | Long | 10000 | `POSTCHAIN_FASTSYNC_JOB_TIMEOUT` |
| `fastsync.loop_interval` | Sleep time between each iteration of a sync job. | Long | 100 | `POSTCHAIN_FASTSYNC_LOOP_INTERVAL` |
| `fastsync.must_sync_until_height` | Minimum height when sync might be considered done. | Long | -1 | `POSTCHAIN_FASTSYNC_MUST_SYNC_UNTIL_HEIGHT` |
| `fastsync.max_errors_before_blacklisting` | Max errors before a peer is blacklisted. | Int | 10 | `POSTCHAIN_FASTSYNC_MAX_ERRORS_BEFORE_BLACKLISTING` |
| `fastsync.disconnect_timeout` | Time before peer is considered disconnected (ms). | Long | 10000 | `POSTCHAIN_FASTSYNC_DISCONNECT_TIMEOUT` |
| `fastsync.blacklisting_timeout` | Max time a node is blacklisted if no errors occur (ms). | Long | 60000 | `POSTCHAIN_FASTSYNC_BLACKLISTING_TIMEOUT` |
| `fastsync.blacklisting_error_timeout` | Max time an error is considered when deciding if a node should be blacklisted (ms). | Long | 3600000 | `POSTCHAIN_FASTSYNC_BLACKLISTING_ERROR_TIMEOUT` |
| `slowsync.enabled` | Is slowsync enabled. | Boolean | true | `POSTCHAIN_SLOWSYNC_ENABLED` |
| `slowsync.max_sleep_time` | Maximum sleep time for slowsync (ms). | Long | 600000 | `POSTCHAIN_SLOWSYNC_MAX_SLEEP_TIME` |
| `slowsync.min_sleep_time` | Minimum sleep time for slowsync (ms). | Long | 20 | `POSTCHAIN_SLOWSYNC_MIN_SLEEP_TIME` |
| `slowsync.max_peer_wait_time` | A peer must answer before this, or we will give up (ms). | Long | 2000 | `POSTCHAIN_SLOWSYNC_MAX_PEER_WAIT_TIME` |
| `applied-config-send-interval-ms` | Interval for signers to broadcast info about their currently applied config (milliseconds) | Long | 1000 | `POSTCHAIN_CONFIG_SEND_INTERVAL_MS` |
| `blockchain_ancestors.<brid_X>` | List of blockchain ancestors [<node_id_Y>:<brid_Z>] | String | | |

### Peers

It is possible to configure peers via node configuration with these properties.

| Name | Description | Type |
| ------ | ------ | ------ |
| `node.<index>.pubkey` | Pubkey of peer | String |
| `node.<index>.host` | Host of peer | String |
| `node.<index>.port` | Peer-to-peer port of peer | Int |

## REST API
| Name                            | Description                                                                                                                                            | Type | Default | Environment Variable |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------| ------ |---------| ------ |
| `api.basepath`                  | The API will be attached under the basepath. Don't append a trailing slash to the basepath. To run on root, leave this empty.                          | String | ""      | `POSTCHAIN_API_BASEPATH` |
| `api.port`                      | REST API port, `-1` will disable the API, `0` will assign to a random free port.                                                                       | Int | 7740    | `POSTCHAIN_API_PORT` |
| `api.request-concurrency`       | Number of incoming HTTP requests to handle concurrently. The default value `0` means the number of available processors * 2.                           | Int | `0`     | `POSTCHAIN_API_REQUEST_CONCURRENCY` |
| `api.chain-request-concurrency` | Number of incoming HTTP requests to handle concurrently per blockchain. Unlimited by default. If exceeded, `503 Service Unavailable` will be returned. | Int | `-1`    | `POSTCHAIN_API_CHAIN_REQUEST_CONCURRENCY` |
| `debug.port`                    | Debug API port.                                                                                                                                        | Int | 7750    | `POSTCHAIN_DEBUG_PORT` |

## Containers (subnodes)

| Name                                                    | Description                                                                                                                                            | Type    | Required           | Default                    | Environment Variable                                    |
|---------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|---------|--------------------|----------------------------|---------------------------------------------------------|
| `container.docker-image`                                | Name of docker image to use                                                                                                                            | String  | :white_check_mark: |                            | `POSTCHAIN_SUBNODE_DOCKER_IMAGE`                        |
| `container.master-host`                                 | Host address of master node (from subnode perspective)                                                                                                 | String  | :white_check_mark: |                            | `POSTCHAIN_MASTER_HOST`                                 |
| `container.master-port`                                 | Master node port for netty communication                                                                                                               | Int     |                    | 9860                       | `POSTCHAIN_MASTER_PORT`                                 |
| `container.network`                                     | Custom docker network                                                                                                                                  | String  |                    |                            | `POSTCHAIN_SUBNODE_NETWORK`                             |
| `container.subnode-host`                                | Subnode host address (from master node perspective)                                                                                                    | String  | :white_check_mark: |                            | `POSTCHAIN_SUBNODE_HOST`                                |
| `container.rest-api-port`                               | Subnode REST API port                                                                                                                                  | Int     |                    | 7740                       | `POSTCHAIN_SUBNODE_REST_API_PORT`                       |
| `container.debug-api-port`                              | Subnode Debug API port                                                                                                                                 | Int     |                    | 7750                       | `POSTCHAIN_SUBNODE_DEBUG_API_PORT`                      |
| `container.admin-rpc-port`                              | Subnode gRPC API port                                                                                                                                  | Int     |                    | 50051                      | `POSTCHAIN_SUBNODE_ADMIN_RPC_PORT`                      |
| `container.subnode-user`                                | Custom user to run docker containers                                                                                                                   | String  |                    |                            | `POSTCHAIN_SUBNODE_USER`                                |
| `container.send-master-connected-peers-period`          | Interval for master node to send list of connected peers to subnode (milliseconds)                                                                     | Int     |                    | 60 000                     | `POSTCHAIN_SEND_MASTER_CONNECTED_PEERS_PERIOD`          |
| `container.healthcheck.running-containers-check-period` | Interval for master node to check that subnodes are healthy (milliseconds)                                                                             | Int     |                    | 60 000                     | `POSTCHAIN_HEALTHCHECK_RUNNING_CONTAINERS_CHECK_PERIOD` |
| `container.filesystem`                                  | Filesystem to use for subnodes (LOCAL, ZFS, EXT4)                                                                                                      | String  |                    | "LOCAL"                    | `POSTCHAIN_SUBNODE_FILESYSTEM`                          |
| `container.host-mount-dir`                              | The directory in host filesystem where subnode mount directories will be created                                                                       | String  | :white_check_mark: |                            | `POSTCHAIN_HOST_MOUNT_DIR`                              |
| `container.host-mount-device`                           | The host device that `container.host-mount-dir` is mounted on. Note: Without the partition name                                                        | String  | :white_check_mark: |                            | `POSTCHAIN_HOST_MOUNT_DEVICE`                           |
| `container.master-mount-dir`                            | In case master node is run inside a container this is the directory in the master container filesystem that will be used for subnode mount directories | String  |                    | `container.host-mount-dir` | `POSTCHAIN_MASTER_MOUNT_DIR`                            |
| `container.bind-pgdata-volume`                          | Bind postgres data directory to host file system (so it's persisted)                                                                                   | Boolean |                    | true                       | `POSTCHAIN_BIND_PGDATA_VOLUME`                          |
| `container.docker-log-driver`                           | Custom docker log driver                                                                                                                               | String  |                    | ""                         | `POSTCHAIN_DOCKER_LOG_DRIVER`                           |
| `container.docker-log-opts`                             | Custom docker log options                                                                                                                              | String  |                    | ""                         | `POSTCHAIN_DOCKER_LOG_OPTS`                             |
| `container.remote-debug-enabled`                        | Run subnodes with remote debugging enabled (Attach to port that is mapped to 8000)                                                                     | Boolean |                    | false                      | `POSTCHAIN_SUBNODE_REMOTE_DEBUG_ENABLED`                |
| `container.remote-debug-suspend`                        | Run remote debug with suspend flag enabled                                                                                                             | Boolean |                    | false                      | `POSTCHAIN_SUBNODE_REMOTE_DEBUG_SUSPEND`                |
| `container.metrics.prometheus.port`                     | Enables prometheus in subnodes on this port (port will not be mapped to host)                                                                          | Int     |                    | -1                         | `POSTCHAIN_SUBNODE_PROMETHEUS_PORT`                     |
| `container.jmx-base-port`                               | Enables JMX on subnodes, JMX port will be mapped to this base value + id of container                                                                  | Int     |                    | -1                         | `POSTCHAIN_SUBNODE_JMX_BASE_PORT`                       |
| `container.label.key1=value1`                           | Set Docker label key1=value1 on the subnode container, can be repeated to set multiple labels                                                          | String  |                    |                            |                                                         |
| `min-space-quota-buffer-mb`                             | Disk space margin to quota limit, container will switch to read-only mode if reaching this                                                             | Int     |                    | 100                        | `POSTCHAIN_SUBNODE_MIN_SPACE_QUOTA_BUFFER_MB`           |
| `container.log4j-configuration-file`                    | Path to log4j configuration file to use for subnodes                                                                                                   | String  |                    |                            | `POSTCHAIN_SUBNODE_LOG4J_CONFIGURATION_FILE`            |
| `container.postgres_max_locks_per_transaction`          | Overrides the postgres setting for `max_locks_per_transaction` in subnode database                                                                     | Int     |                    | 1024                       | `POSTCHAIN_SUBNODE_POSTGRES_MAX_LOCKS_PER_TRANSACTION`  |

### ZFS
Relevant when `container.filesystem` is configured as `ZFS`.

| Name | Description | Type | Default | Environment Variable |
| ------ | ------ | ------ | ------ | ------ |
| `container.zfs.pool-name` | Name of ZFS pool | String | "psvol" | `POSTCHAIN_ZFS_POOL_NAME` |
| `container.zfs.pool-init-script` | Script for setting up ZFS | String | | `POSTCHAIN_ZFS_POOL_INIT_SCRIPT` |