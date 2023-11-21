This is an attempt to provide a list of possible blockchain configuration (for `BaseBlockChainConfiguration`). For a
list of _node_ configuration properties see this [page](Node-Configuration-Properties.md).

| Name                             | Description                                                                                                               | Type          | Required           | Default |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------|---------------|--------------------|---------|
| `signers`                        | List of bc signers                                                                                                        | array<bytea>  | :white_check_mark: |         |
| `sync`                           | Synchronization infrastructure implementation                                                                             | string        |                    | ""      |
| `sync_ext`                       | Synchronization infrastructure extensions                                                                                 | array<string> |                    | []      |
| `configurationfactory`           | Configuration factory implementation                                                                                      | string        | :white_check_mark: |         |
| `txqueuecapacity`                | Maximum size of the transaction queue                                                                                     | int           |                    | 2500    |
| `historic_brid`                  | Historical brid when forking a blockchain                                                                                 | bytea         |                    |         |
| `dependencies`                   | Blockchain dependencies in raw format                                                                                     | gtv           |                    |         |
| `config_consensus_strategy`      | Configuration consensus strategy                                                                                          | string        |                    |         |
| `query_cache_ttl_seconds`        | How long a query response can be cached, in seconds. 0 means no caching.                                                  | int           |                    | 0       |
| `max_block_future_time`          | How long time in the future a block timestamp can be, compared to current time, in milliseconds. -1 means disabled check. | int           |                    | 10000   |

## Block strategy

Configuration under the key `blockstrategy`.

| Name                           | Description                                                                                                                  | Type    | Default          |
|--------------------------------|------------------------------------------------------------------------------------------------------------------------------|---------|------------------|
| `name`                         | Block strategy, name of class implementing `net.postchain.core.block.BlockBuildingStrategy`                                  | string  |                  | net.postchain.base.BaseBlockBuildingStrategy |
| `maxblocksize`                 | Maximum size of a block in bytes                                                                                             | int     | 26 * 1024 * 1024 |
| `maxblocktransactions`         | Maximum transactions per block                                                                                               | int     | 100              |
| `mininterblockinterval`        | Smallest time interval between blocks in milliseconds                                                                        | int     | 25               |
| `maxblocktime`                 | Maximum time to wait before starting to build a block in milliseconds. Will be an empty block if transaction queue is empty. | int     | 30 000           |
| `maxtxdelay`                   | Maximum time to wait before starting to build a block after first transaction was received, in milliseconds                  | int     | 1000             |
| `minbackofftime`               | Minimum back-off time before retrying a failed block, in milliseconds                                                        | int     | 20               |
| `maxbackofftime`               | Maximum back-off time before retrying a failed block, in milliseconds                                                        | int     | 2000             |
| `maxspecialendtransactionsize` | Maximum size of special end transaction, in bytes                                                                            | int     | 1024             |
| `preemptiveblockbuilding`      | Enable preemptive block building                                                                                             | boolean | true             |

## GTX

Configuration under the key `gtx`.

| Name                   | Description                                                                             | Type          | Default          |
|------------------------|-----------------------------------------------------------------------------------------|---------------|------------------|
| `max_transaction_size` | Maximum size of transactions in bytes                                                   | int           | 25 * 1024 * 1024 |
| `modules`              | GTX modules                                                                             | array<string> |                  |
| `sqlmodules`           | GTX SQL modules                                                                         | array<string> |                  |
| `allowoverrides`       | Allow operations and queries with the same name to be overridden by another GTX module. | boolean       | false            |

## Revolt

Configuration under the key `revolt`.

| Name                             | Description                                                                                                                                                   | Type    | Default |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------|
| `timeout`                        | The default revolt timeout in milliseconds                                                                                                                    | int     | 10 000  |
| `exponential_delay_initial`      | The initial delay of revolt timeout in milliseconds                                                                                                           | int     | 1000    |
| `exponential_delay_power_base`   | Power base of the exponential increase of the revolt timeout per round                                                                                        | string  | 1.2     |
| `exponential_delay_max`          | Maximum possible revolt timeout in milliseconds                                                                                                               | int     | 600 000 |
| `fast_revolt_status_timeout`     | Timeout in milliseconds since last received status message before considering a node disconnected and revolting immediately. -1 to disable this functionality | int     | -1      |
| `revolt_when_should_build_block` | Only start counting revolt timeout after we ourselves consider it possible to actually build a block                                                          | boolean | false   |
