This is an attempt to provide a list of possible blockchain configuration (for `BaseBlockChainConfiguration`). For a list of _node_ configuration properties see this [page](Node-Configuration-Properties.md).

| Name | Description                                                                | Type          | Required | Default |
| ------ |----------------------------------------------------------------------------|---------------| ------ |---------|
| `signers` | List of bc signers                                                         | array<bytea>  | :white_check_mark: |         |
| `sync` | Synchronization infrastructure implementation                              | string        | | ""      |
| `sync_ext` | Synchronization infrastructure extensions                                  | array<string> | | []      |
| `configurationfactory` | Configuration factory implementation                                       | string        | :white_check_mark: |         |
| `txqueuecapacity` | Maximum size of the transaction queue                                      | int           | | 2500    |
| `historic_brid` | Historical brid when forking a blockchain                                  | bytea         | |         |
| `dependencies` | Blockchain dependencies in raw format                                      | gtv           | |         |
| `max_transaction_execution_time` | Maximum execution time for a tx before it will be rejected, disabled if -1 | int           | | -1      |
| `config_consensus_strategy` | Configuration consensus strategy                                           | string        | | |

## Block strategy
Configuration under the key `blockstrategy`.

| Name                    | Description                                                                                                                  | Type    | Default          |
|-------------------------|------------------------------------------------------------------------------------------------------------------------------|---------|------------------|
| `name`                  | Block strategy, name of class implementing `net.postchain.core.block.BlockBuildingStrategy`                                  | string  |                  | net.postchain.base.BaseBlockBuildingStrategy |
| `maxblocksize`          | Maximum size of a block in bytes                                                                                             | int     | 26 * 1024 * 1024 |
| `maxblocktransactions`  | Maximum transactions per block                                                                                               | int     | 100              |
| `mininterblockinterval` | Smallest time interval between blocks in milliseconds                                                                        | int     | 25               |
| `maxblocktime`          | Maximum time to wait before starting to build a block in milliseconds. Will be an empty block if transaction queue is empty. | int     | 30 000           |
| `maxtxdelay`            | Maximum time to wait before starting to build a block after first transaction was received, in milliseconds                  | int     | 1000             |
| `minbackofftime`        | Minimum back-off time before retrying a failed block, in milliseconds                                                        | int     | 20               |
| `maxbackofftime`        | Maximum back-off time before retrying a failed block, in milliseconds                                                        | int     | 2000             |
| `maxspecialendtransactionsize`        | Maximum size of special end transaction, in bytes                                                                            | int     | 1024             |
| `preemptiveblockbuilding`        | Enable preemptive block building                                                                                             | boolean | true             |

## GTX
Configuration under the key `gtx`.

| Name | Description | Type          | Default          |
| ------ | ------ |---------------|------------------|
| `max_transaction_size` | Maximum size of transactions in bytes | int           | 25 * 1024 * 1024 |
| `modules` | GTX modules | array<string> |                  |
| `sqlmodules` | GTX SQL modules | array<string> |                  |
| `allowoverrides` | Allow operations and queries with the same name to be overridden by another GTX module. | boolean       | false            |


## Revolt
Configuration under the key `revolt`.

| Name | Description | Type | Default |
| ------ | ------ | ------ | ------ |
| `timeout` | The default revolt timeout in milliseconds | int | 10 000 |
| `exponential_delay_base` | Base of the exponential increase of the revolt timeout per round | int | 1000 |
| `exponential_delay_max` | Maximum possible revolt timeout | int | 600 000 |
| `fast_revolt_status_timeout` | Timeout since last received status message before considering a node disconnected and revolting immediately. -1 to disable this functionality | int | -1 |
