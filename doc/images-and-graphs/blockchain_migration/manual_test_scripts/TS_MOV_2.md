## TS_MOV_2: 4-node network -- move

# 1. Preconditions / setup

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
╭─────────────────┬────╮
│ Anchored height │ 91 │
├─────────────────┼────┤
│ 0350FE40        │ 93 │
├─────────────────┼────┤
│ 03567610        │ 93 │
╰─────────────────┴────╯
```

3. Pause blockchain before moving
```shell
alpha> pmc blockchain stop -brid $CITIES
beta> pmc proposal vote -y --id 462
```

4. Verify that dapp is PAUSED
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
│ State           │ PAUSED                                                           │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Container       │ c1                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Cluster         │ s1                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Is system chain │ false                                                            │
╰─────────────────┴──────────────────────────────────────────────────────────────────╯
Heights on nodes:
╭─────────────────┬─────╮
│ Anchored height │ 149 │
├─────────────────┼─────┤
│ 0350FE40        │ 150 │
├─────────────────┼─────┤
│ 03567610        │ 150 │
╰─────────────────┴─────╯
```

5. Initiate blockchain moving
```shell
alpha> pmc blockchain move -brid $CITIES -dc c2
gamma> pmc proposal vote -y --id 485
delta> pmc proposal vote -y --id 485
```

6. Verify that blockchain is moving
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
│ State           │ PAUSED                                                           │
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
│ 03EF3F5B        │ 125 │
├─────────────────┼─────┤
│ 03F811D3        │ 29  │
╰─────────────────┴─────╯
Moving blockchain info:
╭───────────────────────┬────╮
│ Source container      │ c1 │
├───────────────────────┼────┤
│ Destination container │ c2 │
├───────────────────────┼────┤
│ Final height          │ -1 │
╰───────────────────────┴────╯
```

7. Once syncing is done, finalize moving at a specific height `last_block_height - 1`
```shell
alpha> pmc blockchain finish-moving -brid $CITIES --final-height 149
gamma> pmc proposal vote -y --id 492
delta> pmc proposal vote -y --id 492
```

8. Verify that `final height` is set and blockchain is in PAUSED state
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
│ State           │ PAUSED                                                           │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Container       │ c2                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Cluster         │ s2                                                               │
├─────────────────┼──────────────────────────────────────────────────────────────────┤
│ Is system chain │ false                                                            │
╰─────────────────┴──────────────────────────────────────────────────────────────────╯
Heights on nodes:
╭─────────────────┬─────╮
│ Anchored height │ 149 │
├─────────────────┼─────┤
│ 03EF3F5B        │ 150 │
├─────────────────┼─────┤
│ 03F811D3        │ 150 │
╰─────────────────┴─────╯
Moving blockchain info:
╭───────────────────────┬─────╮
│ Source container      │ c1  │
├───────────────────────┼─────┤
│ Destination container │ c2  │
├───────────────────────┼─────┤
│ Final height          │ 149 │
╰───────────────────────┴─────╯
```

9. Resume the blockchain
```shell
alpha> pmc blockchain start -brid $CITIES
gamma> pmc proposal vote -y --id 499
delta> pmc proposal vote -y --id 499
```

10. When moving is finished, verify that blockchain is RUNNING
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
│ Anchored height │ 160 │
├─────────────────┼─────┤
│ 03EF3F5B        │ 161 │
├─────────────────┼─────┤
│ 03F811D3        │ 162 │
╰─────────────────┴─────╯
```


