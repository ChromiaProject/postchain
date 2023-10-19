# EBFT States and Intents

A node is always in a state and has an intent. These control the actions of the node in the EBFT state machine.

## EBFT States

A node can be in one of three states depending on where the node is in the block building lifecycle.

| Name        | Description                                                                                    | Enter condition                                                                                           |
|-------------|------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `WaitBlock` | While waiting for a new block to be built by primary we reside in state `WaitBlock`.           | When no block is being handled.                                                                           |
| `HaveBlock` | While waiting for consensus to have fetched block from primary we reside in `HaveBlock` state. | If node is primary then enter when block is built, otherwise when received unfinished block from primary. |
| `Prepared`  | While waiting for consensus to have signed block we reside in `Prepared` state.                | When at least 2f+1 nodes are in `HaveBlock` or `Prepared` state for same block as node has.               |

## Intent

Apart from the states a node can be in it also have internal intents depending on what needs to be done. 

| Intent                       | Valid in state | Description                                                                                                                                                             |
|------------------------------|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DoNothingIntent`            | All            |                                                                                                                                                                         |
| `BuildBlockIntent`           | `WaitBlock`    | Only applicable if we are primary node. Indicate that we intend to build block if possible.                                                                             |
| `FetchBlockAtHeightIntent`   | `WaitBlock`    | If fewer then 2f+1 nodes are on current node height and at least one other node is on higher height we enter `FetchBlockAtHeightIntent` to try to fetch missing blocks. |
| `FetchUnfinishedBlockIntent` | `WaitBlock`    | If primary node has built a block, indicated by `Status` message from primary, we enter `FetchUnfinishedBlockIntent` to fetch the unfinished block.                     |
| `FetchCommitSignatureIntent` | `Prepared`     | If we do not have consensus and signature from nodes in `Prepared` state for current block we enter `FetchCommitSignatureIntent` to fetch the missing signatures.       |
| `CommitBlockIntent`          | `Prepared`     | If more then 2f+1 nodes have signed the block we enter `CommitBlockIntent` to commit the block.                                                                         |
