# Messages

Overview of messages added/modified/deprecated in EBFT version 2.

## Added messages

The following messages were added:

| Name          | Description                           | Parameters        | Parameters description                                        |
|---------------|---------------------------------------|-------------------|---------------------------------------------------------------|
| `EbftVersion` | Latest supported EBFT version of node | ebftVersion: Long | ebftVersion - Version number of latest supported EBFT version |

## Modified messages

The following messages were modified:

| Name     | Description             | Parameters                                                                                                                                                                               | Parameters description                                                                                                                                                                                                                                                                                                                                                                                       |
|----------|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Status` | Current status of node. | blockRID: ByteArray?<br/>height: Long<br/>revolting: Boolean<br/>round: Long<br/>serial: Long<br/>state: Int<br/>signature: Signature? **(Added)**<br/>configHash: ByteArray **(Added)** | blockRID - RID for current block that is being processed or null if no block is being processed<br/>height - current height<br/>revolting - is the node revolting<br/>round - current round<br/>serial - current serial<br/>state - current EBFT state<br/>signature - node signature for current block or null if no block is being processed<br/>configHash - Hash of currently applied configuration<br/> |

## Deprecated messages

The following messages were deprecated:

| Name            | Reason                                                          | 
|-----------------|-----------------------------------------------------------------|
| `AppliedConfig` | Not needed since applied config is included in `Status` message |

We do not actually remove any message types but these should no longer be sent between nodes that are both on version 2.

## Versions

Now that EBFT itself is versioned we hope to not change this document but rather create a new EBFT version.