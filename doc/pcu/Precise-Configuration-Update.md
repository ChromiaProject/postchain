# Precise Configuration Update (PCU)

The purpose of this document is to describe what PCU is and how it works.
PCU is a managed mode concept and will only work in conjunction with the standard directory chain implementation.

## Glossary

Terms used in the document:

* CAC: Cluster Anchoring Chain
* SAC: System Anchoring Chain
* Early adopter: A node that has applied a new pending configuration before the current primary
* Late adopter: A node that has not yet applied a new pending configuration that the current primary is using

## Use case

The point of PCU is to automate the process of selecting the height for a new configuration. Without PCU a maintainer
of a blockchain must guess what would be an appropriate height to apply a new configuration. A height in the near future
can be chosen but then the risk is that the height has passed before the signers have had time to apply it. This would
mean that the directory chain is holding incorrect information about which height the configuration applies to
(making it impossible to sync the chain without manual intervention). The maintainer can also opt to be on the safe side
and propose the configuration at a height far in the future, the drawback of this is obvious, especially for urgent
updates.

## Requirements

There are a few requirements for PCU to work. These are not strictly enforced by postchain but rather in directory
chain.

1. Current configuration hash must be included in the block header. Both as a consensus mechanism for the active
   configuration but also so that the anchoring chains can detect configuration updates.
2. Configurations must be unique
3. Configuration updates must not add/remove more than a superminority (< 1 / 3) of signers

The first point is probably self-explanatory and the second is due to implementation details.

The third point might not be so clear. The problem with allowing changing multiple signers at the same time is that it
can allow forking of the chain to occur. Imagine the extreme case where signers are completely replaced in a pending
configuration, then both a block built by the old signers and a block built by the new signers are valid. It is easy to
see that this is still possible when the signer sets partially intersect. Is it a problem? We can just disregard the
short-lived fork built by the old signers? Well, it is possible that the anchoring chain anchored the block built on
that fork, then we are in trouble.

## General case

New configurations will be proposed to the directory chain. The directory chain will ensure that the configuration is
unique by injecting some values into it. Now the configuration is considered "pending" with a minimum allowed height
that it can be applied at.

As per normal postchain operations a node will check if any new configurations should be applied after loading a block.
Nodes will now also consider pending configurations and try to reach consensus on which configuration to use.

### Primary

The primary node more or less operates the same as without PCU. It will attempt to build and propose a block with its
currently applied configuration, whether it is pending or not.

### Early adopters

An early adopter will be unable to load a block from the primary node. It will realize that it is early and restart the
chain with the previously active configuration.

### Late adopters

A late adopter will be unable to load a block from the primary node. If it has not seen the new pending configuration,
yet it has no choice but to reject the block for the time being. If it has seen the new pending configuration it will
instead restart the chain with that configuration.

### Signalling active configuration

Signer nodes will also actively broadcast information about currently applied configuration. The reason why will be
explained in "Replica" and "Edge cases" sections.

### Replicas

Since replicas are not participating in forming consensus on new configuration updates they will not consider pending
configurations.

The only exception is if the replica is promoted to signer in the pending configuration and is needed to reach
consensus. It will then wait until all the current signers have switched to the new pending configuration until loading
it. Block building can then resume.

### Failed configurations

If a node fails to load a configuration, either by failing the validation/initialization stage or failing to build the
first block with it, it will revert the update and fall back to the previous configuration. It will then try to reach
consensus that the configuration is faulty by putting the configuration hash in a "faulty" field in the block header. If
the node is an early adopter it will give up and try reporting it again on the next block.

### Reporting

CAC can detect that a chain has updated its configuration by inspecting the block header. It will notify the directory
chain that the pending configuration has been applied by sending an ICMF message.

SAC will similarly monitor and report updates on CACs. It will also self-report if its own configuration has been
updated.

Once directory chain receives the report that the configuration has been applied it will be removed as pending and
stored as the actual configuration at the applied height.

The same reporting procedure is used for failed configurations. Once the directory chain receives a report of a failed
configuration it will be removed as pending, and it will be recorded as a failed configuration attempt.

## Edge cases

This section is including some edge cases that might be worth researching if they can be covered in some other way.

One area to investigate is if active configuration broadcast can be removed, however, that would require the logic for
replicas to change. They would have to continually check if any new pending configurations has been added if no new
blocks are being built for a long time. This is since we need to cover the case when they are promoted and needed for
forming consensus. Such polling does not seem like a clean solution.

Adding the signalling of active configuration did have somewhat of an impact on performance in EBFT version 1 since
they were sent in separate messages. In version 2 it is included in the status message and the overhead of doing that
is most likely negligible. That makes this topic less interesting to pursue.

### Late adopter and removed primary

Consider a chain with signer `A` and `B`. A pending configuration update removes `A` as signer. `A` is an early adopter
and `B` is late. `A` sees the new configuration and stops running the chain. `B` has not seen it and is waiting for `A`
to build a block since it is `A`:s turn. This will never happen, `B` will revolt but needs `A` to revolt as well to get
a successful revolt. Now we are in a stuck state.

This is solved by checking for new pending configurations if we are revolting and less than a BFT-majority of signers
are live (sending us status messages).

### Replica is waiting for late adopter

Consider a chain with signers `A` and `B`. `A` is an early adopter, `B` is a late adopter and `C` is added as signer in
a new pending configuration. `C` will wait until `A` and `B` has applied the new configuration. Based on round-robin
calculations `A` considers `C` as primary, `B` considers `A` to be primary. This results in no nodes building a block.
Both `A` and `B` will revolt but only `B` considers the revolt successful. Now `B` moves to the next round and considers
itself as primary but `A` will not accept blocks from `B` since it still considers `C` as primary. Now the chain is
stuck with `A` and `B` revolting and neither considering the revolt to be successful.

The solution is to let revolting signers inspect incoming active configuration broadcasts to see if they are late
adopters. In the example above `B` will do so and apply the new configuration. This will also allow `C` to follow and
apply the new configuration.

This could potentially also be solved by relaxing the condition for promoted replica nodes that states that they need to
wait until current signers have loaded a pending configuration before loading it themselves. It seems that was mainly
added to tackle the case where multiple nodes are promoted and a fork is risked. However, now we have the soft
requirement for PCU that only one signer can be added per update and this mechanism is anyway by no means enough
protection against the fork scenario. It does however rhyme well with how replicas handle PCU in general, they are
reactive and try to involve themselves as little as possible until consensus have been reached on a new configuration.
