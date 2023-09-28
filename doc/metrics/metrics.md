# Postchain node metrics

A postchain node can expose metrics to [Prometheus](https://prometheus.io/), configured with node configuration property 
`metrics.prometheus.port` or environment variable `POSTCHAIN_PROMETHEUS_PORT`.

Note that these metrics represent the particular node's status and performance, which may not necessarily correspond with other nodes', 
nor with the cluster overall. Great care should be taken if aggregating these metrics over the nodes in a cluster. 

## Exposed metrics

| Name                                    | Type    | Description                                                                                                                                                                                                          | Tags                               |
|-----------------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------| 
| blockchains                             | gauge   | Number of blockchains currently running on node.                                                                                                                                                                     |                                    |
| subnodes                                | gauge   | Number of subnodes which should be running on node.                                                                                                                                                                  |                                    |
| containers                              | gauge   | Number of containers which should be running on node.                                                                                                                                                                |                                    |
| submitted_transactions,result=OK        | timer   | Transactions submitted by client and enqueued.                                                                                                                                                                       | chainIID, blockchainRID            |
| submitted_transactions,result=INVALID   | timer   | Transactions submitted by client and rejected due to being invalid.                                                                                                                                                  | chainIID, blockchainRID            |
| submitted_transactions,result=DUPLICATE | timer   | Transactions submitted by client and rejected due to being duplicate.                                                                                                                                                | chainIID, blockchainRID            |
| submitted_transactions,result=FULL      | timer   | Transactions submitted by client and rejected due to queue full.                                                                                                                                                     | chainIID, blockchainRID            |
| transaction_queue_size                  | gauge   | Current size of transaction queue.                                                                                                                                                                                   | chainIID, blockchainRID            |
| processed_transactions,result=ACCEPTED  | timer   | Transactions picked from queue and added to an unfinished block. Note that the block may be rejected by other nodes and never committed, the same transaction will then be processed again, by this or another node. | chainIID, blockchainRID            |
| processed_transactions,result=REJECTED  | timer   | Transactions picked from queue and rejected.                                                                                                                                                                         | chainIID, blockchainRID            |
| blocks                                  | timer   | Built blocks. Note that the block may be rejected by other nodes and never committed.                                                                                                                                | chainIID, blockchainRID            | 
| signedBlocks                            | timer   | Signed blocks. Note that the block may be rejected by other nodes and never committed.                                                                                                                               | chainIID, blockchainRID            | 
| blockHeight                             | counter | Current block height.                                                                                                                                                                                                | chainIID, blockchainRID            |
| RevoltsOnNode                           | counter | Number of revolts towards node.                                                                                                                                                                                      |                                    |
| RevoltsByNode                           | counter | Number of revolts node has done.                                                                                                                                                                                     |                                    |
| RevoltsBetweenOtherNodes                | counter | Number of revolts between other nodes.                                                                                                                                                                               |                                    |
| queries,result=success                  | timer   | Successful queries.                                                                                                                                                                                                  | chainIID, blockchainRID, queryName |
| queries,result=failure                  | timer   | Failed queries.                                                                                                                                                                                                      | chainIID, blockchainRID, queryName |

All those metrics also have `node_pubkey` tag.

In addition to this, a standard set of JVM and machine metrics are also exposed.

## Metric tags

| Name          | Description                             |
|---------------|-----------------------------------------|
| node_pubkey   | Node public key (hex encoded)           |
| chainIID      | blockchain IID (numeric, node specific) |
| blockchainRID | blockchain RID (hex encoded, global)    |
| queryName     | Query name                              |
