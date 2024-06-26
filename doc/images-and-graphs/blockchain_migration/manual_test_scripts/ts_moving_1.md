## TS_MOVING_1: 1-node network -- move

1. Preconditions / setup

Create cluster / container pairs: s1/c1, s2/c2
```shell
pmc cluster add -n s1 --pubkeys $ALPHA -g SYSTEM_P
pmc container add -n c1 -c s1 --pubkeys $ALPHA
pmc node update --pubkey $NODE0 --cluster-units 3 --cluster s1
pmc cluster add -n s2 --pubkeys $ALPHA -g SYSTEM_P
pmc container add -n c2 -c s2 --pubkeys $ALPHA
pmc node update --pubkey $NODE0 --cluster s2
```

deploy dapp to s1/c1
```shell
pmc blockchain add -bc ./dapp/build/0.xml -c c1 -n cities
export CITIES=4114BA9A69BEFE60DEA391D7EF2EE5C40CA6654654BF51207AFC45798AAD6525
```

2. Verify that dapp is running
```shell
pmc blockchain info -brid $CITIES
```
```shell
Basic info:
╭─────────────────┬──────────────────────────────────────────────────────────────────╮
│ Name            │ cities                                                           │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ RID             │ 4114BA9A69BEFE60DEA391D7EF2EE5C40CA6654654BF51207AFC45798AAD6525 │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ State           │ RUNNING                                                          │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Container       │ c1                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Cluster         │ s1                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Is system chain │ false                                                            │
╰─────────────────┴──────────────────────────────────────────────────────────────────╯
Heights on nodes:
╭─────────────────┬────╮
│ Anchored height │ 49 │
├─────────────────┼────┤
│ 0350FE40        │ 51 │
╰─────────────────┴────╯
```

3. Pause blockchain before moving
```shell
pmc blockchain stop -brid $CITIES
```

4. Initiate blockchain moving
```shell
pmc blockchain move -brid $CITIES -dc c2
```

5. Verify that blockchain is moving
```shell
pmc blockchain info -brid $CITIES
```
```shell
Basic info:
╭─────────────────┬──────────────────────────────────────────────────────────────────╮
│ Name            │ cities                                                           │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ RID             │ 4114BA9A69BEFE60DEA391D7EF2EE5C40CA6654654BF51207AFC45798AAD6525 │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ State           │ PAUSED                                                           │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Container       │ c2                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Cluster         │ s2                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Is system chain │ false                                                            │
╰─────────────────┴──────────────────────────────────────────────────────────────────╯
Heights on nodes:
╭─────────────────┬────╮
│ Anchored height │ 58 │
├─────────────────┼────┤
│ 0350FE40        │ 59 │
╰─────────────────┴────╯
Moving blockchain info:
╭───────────────────────┬────╮
│ Source container      │ c1 │
├───────────────────────┼────┤
│ Destination container │ c2 │
├───────────────────────┼────┤
│ Finish at height      │ -1 │
╰───────────────────────┴────╯
```

6. Verify that both docker containers `0350fe40-c1-1` and `0350fe40-c2-2` are running
```shell
docker ps -a
```

7. Finish moving at a specific height `last_block_height - 1`
```shell
pmc blockchain finish-moving -brid $CITIES --final-height 58
```

8. Resume the blockchain
```shell
pmc blockchain start -brid $CITIES
```

9. When moving is finished, verify that blockchain is RUNNING
```shell
pmc blockchain info -brid $CITIES
```
```shell
Basic info:
╭─────────────────┬──────────────────────────────────────────────────────────────────╮
│ Name            │ cities                                                           │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ RID             │ 4114BA9A69BEFE60DEA391D7EF2EE5C40CA6654654BF51207AFC45798AAD6525 │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ State           │ RUNNING                                                          │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Container       │ c2                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Cluster         │ s2                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Is system chain │ false                                                            │
╰─────────────────┴──────────────────────────────────────────────────────────────────╯
Heights on nodes:
╭─────────────────┬────╮
│ Anchored height │ 64 │
├─────────────────┼────┤
│ 0350FE40        │ 65 │
╰─────────────────┴────╯
```

10. Verify that docker container `0350fe40-c2-2` is running, and `0350fe40-c1-1` stops in 5 min
```shell
docker ps -a
```

