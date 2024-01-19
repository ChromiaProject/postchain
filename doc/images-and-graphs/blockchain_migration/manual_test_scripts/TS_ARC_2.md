## TS_ARC_1: 4-node network -- archive/unarchive

1. Preconditions / setup

Create cluster s1 and container c1 on nodes: alpha/node0, beta/node1:
```shell
pmc cluster add -n s1 --pubkeys $ALPHA,$BETA -g SYSTEM_P
pmc container add -n c1 -c s1 --pubkeys $ALPHA,$BETA
alpha> pmc node update --pubkey $NODE0 --cluster s1
beta> pmc node update --pubkey $NODE1 --cluster s1
```

Create cluster s2 and container c2 on nodes: gamma/node2, delta/node3:
```shell
pmc cluster add -n s2 --pubkeys $GAMMA,$DELTA -g SYSTEM_P
pmc container add -n c2 -c s2 --pubkeys $GAMMA,$DELTA
gamma> pmc node update --pubkey $NODE2 --cluster s2
delta> pmc node update --pubkey $NODE3 --cluster s2
```

make `$ALPHA` deployer to c2
```shell
pmc voterset info -n container_c2_deployer
alpha> pmc voterset update -vs container_c2_deployer --add-member $ALPHA
beta> pmc proposal vote -y --id 478
gamma> pmc proposal vote -y --id 478
pmc voterset info -n container_c2_deployer
```

deploy dapp to s1/c1
```shell
alpha> pmc blockchain add -bc ./dapp/build/0.xml -c c1 -n cities
beta> pmc proposal vote -y --id 462
export CITIES=92275A976366A095B5F183F5F4DC274E505D254577166D3006A376B5271E3F4D
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
│ RID             │ 92275A976366A095B5F183F5F4DC274E505D254577166D3006A376B5271E3F4D │
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
│ Anchored height │ 278 │
├─────────────────┼─────┤
│ 0350FE40        │ 280 │
├─────────────────┼─────┤
│ 03567610        │ 280 │
╰─────────────────┴─────╯
```

3. Archive blockchain
```shell
alpha> pmc blockchain archive -brid $CITIES
beta> pmc proposal vote -y --id 421
```

4. Verify that the blockchain is in an ARCHIVED state. Note that only `Anchored height` is accessible for archived blockchains.
```shell
pmc blockchain info -brid $CITIES
```
```shell
Basic info:
╭─────────────────┬──────────────────────────────────────────────────────────────────╮
│ Name            │ cities                                                           │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ RID             │ 92275A976366A095B5F183F5F4DC274E505D254577166D3006A376B5271E3F4D │
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
│ Anchored height │ 298 │
├─────────────────┼─────┤
│ 0350FE40        │ -1  │
├─────────────────┼─────┤
│ 03567610        │ -1  │
╰─────────────────┴─────╯
```

5. Unarchive the blockchain to s2/c2. Use `Anchored height` as the final height.
```shell
# pmc blockchain unarchive -brid $CITIES -dc c2 --final-height <anchored_height>
alpha> pmc blockchain unarchive -brid $CITIES -dc c2 --final-height 298
beta> pmc proposal vote -y --id 426
```

6. Verify that blockchain is in UNARCHIVING state
```shell
pmc blockchain info -brid $CITIES
```
```shell
Basic info:
╭─────────────────┬──────────────────────────────────────────────────────────────────╮
│ Name            │ cities                                                           │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ RID             │ 92275A976366A095B5F183F5F4DC274E505D254577166D3006A376B5271E3F4D │
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
│ Anchored height │ 254 │
├─────────────────┼─────┤
│ 03EF3F5B        │ 299 │
├─────────────────┼─────┤
│ 03F811D3        │ 299 │
╰─────────────────┴─────╯
Unarchiving blockchain info:
╭───────────────────────┬─────╮
│ Source container      │ c1  │
├───────────────────────┼─────┤
│ Destination container │ c2  │
├───────────────────────┼─────┤
│ Final height          │ 298 │
╰───────────────────────┴─────╯
```

7. When UNARCHIVING is finished, verify that blockchain is in RUNNING state
```shell
pmc blockchain info -brid $CITIES
```
```shell
Basic info:
╭─────────────────┬──────────────────────────────────────────────────────────────────╮
│ Name            │ cities                                                           │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ RID             │ 92275A976366A095B5F183F5F4DC274E505D254577166D3006A376B5271E3F4D │
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
│ Anchored height │ 303 │
├─────────────────┼─────┤
│ 03EF3F5B        │ 305 │
├─────────────────┼─────┤
│ 03F811D3        │ 305 │
╰─────────────────┴─────╯
```


