## TS_ARCHIVING_1: 1-node network -- archive/unarchive

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
╭─────────────────┬─────╮
│ Anchored height │ 121 │
├─────────────────┼─────┤
│ 0350FE40        │ 123 │
╰─────────────────┴─────╯
```

3. Archive blockchain
```shell
pmc blockchain archive -brid $CITIES
```

4. Verify that blockchain is in ARCHIVED state
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
│ State           │ ARCHIVED                                                         │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Container       │ c1                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Cluster         │ s1                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Is system chain │ false                                                            │
╰─────────────────┴──────────────────────────────────────────────────────────────────╯
Heights on nodes:
╭─────────────────┬─────╮
│ Anchored height │ 137 │
├─────────────────┼─────┤
│ 0350FE40        │ -1  │
╰─────────────────┴─────╯
```

5. Verify that docker container `0350fe40-c1-1` stops in 5 min
```shell
docker ps -a
```

6. Unarchive to s2/c2
```shell
# pmc blockchain unarchive -brid $CITIES -dc c2 --final-height <anchored_height>
pmc blockchain unarchive -brid $CITIES -dc c2 --final-height 137
```

7. Verify that both docker containers `0350fe40-c1-1` and `0350fe40-c2-2` are running
```shell
docker ps -a
```

8. Verify that blockchain is in UNARCHIVING state
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
│ State           │ UNARCHIVING                                                      │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Container       │ c2                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Cluster         │ s2                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Is system chain │ false                                                            │
╰─────────────────┴──────────────────────────────────────────────────────────────────╯
Heights on nodes:
╭─────────────────┬─────╮
│ Anchored height │ -1  │
├─────────────────┼─────┤
│ 0350FE40        │ 139 │
╰─────────────────┴─────╯
Unarchiving blockchain info:
╭───────────────────────┬─────╮
│ Source container      │ c1  │
├───────────────────────┼─────┤
│ Destination container │ c2  │
├───────────────────────┼─────┤
│ Finish at height      │ 137 │
╰───────────────────────┴─────╯
```

9. When UNARCHIVING is finished, verify that blockchain is in RUNNING state
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
╭─────────────────┬─────╮
│ Anchored height │ 185 │
├─────────────────┼─────┤
│ 0350FE40        │ 186 │
╰─────────────────┴─────╯
```

10. Verify that docker container `0350fe40-c2-2` is running, and `0350fe40-c1-1` stops in 5 min
```shell
docker ps -a
```
