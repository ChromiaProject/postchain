# Messages

Overview of messages used between nodes.

Apart from the messages used for EBFT, there are also messages to support other activities withing postchain.

## Encoding

All messages are signed and Gtv encoded.

## EBFT Messages

The following messages are used between nodes for EBFT.

| Name                     | Description                                                                                                                                                                                                                          | Parameters                                                                                                   | Parameters description                                                                                                                                                                                                                                 |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Status`                 | Current status of node.                                                                                                                                                                                                              | blockRID: ByteArray?<br/>height: Long<br/>revolting: Boolean<br/>round: Long<br/>serial: Long<br/>state: Int | blockRID - RID for current block that is being processed or null if no block is being processed<br/>height - current height<br/>revolting - is the node revolting<br/>round - current round<br/>serial - current serial<br/>state - current EBFT state |
| `Transaction`            | Contains transaction.                                                                                                                                                                                                                | data: ByteArray                                                                                              | data - transaction                                                                                                                                                                                                                                     |
| `GetBlockSignature`      | Get commit signature for a block.                                                                                                                                                                                                    | blockRID: ByteArray                                                                                          | blockRID - RID for block                                                                                                                                                                                                                               |
| `BlockSignature`         | Contains commit signature for block. Response to `GetBlockSignature`.                                                                                                                                                                | blockRID: ByteArray<br/>subjectID: ByteArray<br/>data: ByteArray                                             | blockRID - RID for block<br/>subjectID - pubkey or hash of pubkey<br/>data - signature data                                                                                                                                                            |
| `GetUnfinishedBlock`     | Get unfinished block for block RID.                                                                                                                                                                                                  | blockRID: ByteArray                                                                                          | blockRID - RID for block                                                                                                                                                                                                                               |
| `UnfinishedBlock`        | Contains header and transactions for unfinished block. Response to `GetUnfinishedBlock` and `GetBlockHeaderAndBlock`.                                                                                                                | header: ByteArray<br/>transactions: Array&lt;ByteArray&gt;                                                   | header - block header<br/>transactions - array of transactions in block                                                                                                                                                                                |
| `GetBlockAtHeight`       | Get complete block at height.                                                                                                                                                                                                        | height: Long                                                                                                 | height - height to get block at                                                                                                                                                                                                                        |
| `CompleteBlock`          | Contains complete data for block. Response to `GetBlockAtHeight`.                                                                                                                                                                    | header: ByteArray<br/>transactions: Array&lt;ByteArray&gt;<br/>height: Long<br/>witness: ByteArray           | header - block header<br/>transactions - array of transactions in block<br/>height - height of block<br/>witness - signatures that signed the block                                                                                                    |
| `GetBlockHeaderAndBlock` | Request block header and block from node at height. If node does not have block at height, respond with block at current height, or empty if no block is available.                                                                  | height: Long                                                                                                 | height - height to get block at                                                                                                                                                                                                                        |
| `BlockHeader`            | Contains block header, witness and requested height. Requested height might not be the same as the height of the included block. If no block was present the header and witness will be empty. Response to `GetBlockHeaderAndBlock`. | header: ByteArray<br/>witness: ByteArray<br/>requestedHeight: Long                                           | header - block header<br/>witness - signatures that signed the block<br/>requestedHeight - height of requested block (__Note:__ Might not be the same as the block included)                                                                           |

## Other Messages

The following messages are used between nodes for internal activities.

| Name                     | Description                                                                                                                                                                                                                          | Parameters                                                                                                   | Parameters description                                                                                                                                                                                                                                 |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GetBlockRange`          | Get all blocks beginning with block at `height`                                                                                                                                                                                      | startAtHeight: Long                                                                                          | startAtHeight - height to start getting blocks from                                                                                                                                                                                                    |
| `BlockRange`             | Contains list of blocks. Response to `GetBlockRange`.                                                                                                                                                                                | startAtHeight: Long<br/>isFull: Boolean<br/>blocks: Array&lt;CompleteBlock&gt;                               | startAtHeight - the height of the first block in the range<br/>isFull - `true` means that we have more blocks but could not fit them in the package<br/>blocks - array of `CompleteBlock`                                                              |
| `AppliedConfig`          | Inform other nodes about which config node is currently using.                                                                                                                                                                       | configHash: ByteArray<br/>height: Long                                                                       | configHash - the hash of the config currently used<br/>height - current height                                                                                                                                                                         |
| `Identification`         | Used for handshake between nodes.                                                                                                                                                                                                    | pubkey: ByteArray<br/>blockchainRID: ByteArray<br/>timestamp: Long                                           | pubkey - pubkey for node<br/>blockchainRID - RID for blockchain<br/>timestamp - time in milliseconds                                                                                                                                                   |

## Versions

This is an overview of the messages for the different versions.

| Version | Changes                                                      |
|---------|--------------------------------------------------------------|
| 1       | First basic implementation                                   |
| 2       | Added `GetBlockRange` and `BlockRange` to support `SlowSync` |
| 3       | Added `AppliedConfig` to support Precise Config Update (PCU) |