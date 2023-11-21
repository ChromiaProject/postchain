# Tweaking postchain performance

If your dApp is having performance issues you should always start by analyzing and optimizing the dApp code.
See https://docs.chromia.com/advancedtopics/analyzing-dapp-code to get an understanding of what can be done to
improve the performance of your dApp.

If you have done everything that can be done when it comes to optimizing your dApp code, this document can help you
improve the performance further by customizing the postchain configuration to match the performance of your dApp
better. The document is divided into common problem areas.

## Revolts

The revolt mechanism prevents us from malicious nodes idling or producing invalid blocks when they are block builders.
However, if revolts occur just because it takes too long for an honest node to build a block it will have a severe
negative impact on the performance of your dApp. You can spot this problem if you see the chain getting stuck with round
number increasing with no new blocks being built even though there are transactions in the queue. There are two main
ways to mitigate this problem. Tweaking the block building strategy configuration is the recommended approach but if you
only have occasional long-running transactions it might be worth considering tweaking revolt configuration.

### Tweaking block building strategy configuration

If you want to avoid revolts you need to ensure that your block building times are well below half the revolt timeout.
This is since primary node needs to build the block and then the other nodes need to load it before the timeout expires.

You can decrease the time to build a block by reducing the max amount of transactions to include in each block. You
can do this by modifying `maxblocktransactions` configuration under `blockstrategy`.

### Tweaking revolt configuration

To avoid revolts it is of course possible to simply increase the timeout. However, as mentioned earlier, the revolt
mechanism is also an important protection against malicious nodes. In order to eventually build a block the
revolt timeout is increased exponentially for each round until the nodes can finally build the block before it expires.
This process can be sped up by modifying revolt configuration. The exponential increase is calculated in the following
way:

`exponential_delay_initial * exponential_delay_power_base ^ round_number`

Both `exponential_delay_initial` and `exponential_delay_power_base` are configurable under `revolt` configuration.

The revolt timeout will be maxed out at the configurable `exponential_delay_max` time. However, instead of increasing
the max timeout, try to find a way to optimize the problematic transactions. Perhaps the work can be split into chunks
or done in another way?

## Transaction delay

You have a chain with few occasional transactions. Your code is well optimized and transactions execute within just a
few milliseconds. Still it takes quite a long time for the transaction to be included in a block. What is going on? By
default, postchain waits a configurable amount of time for more transactions that can be included in the currently built
block. Normally this is good since there is an overhead in building a block and you want to pack them with as many
transactions as possible. However, If transactions are few this waiting time is most likely useless idle time. You can
lower this delay by tweaking `maxtxdelay` under `blockstrategy`.
