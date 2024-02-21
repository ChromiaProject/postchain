#!/bin/sh
set -e

# See ts_moving_2.md

export NODE0=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
export NODE1=035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9
export NODE2=03EF3F5BE98D499B048BA28B247036B611A1CED7FCF87C17C8B5CA3B3CE1EE23A4
export NODE3=03F811D3E806E6D093A4BCCE49C145BA78F9A4B2FBD167753ECAB2A13530B081F8

export API0=https://node0.devnet2.chromia.dev:7740
export API1=https://node1.devnet2.chromia.dev:7740
export API2=https://node2.devnet2.chromia.dev:7740
export API3=https://node3.devnet2.chromia.dev:7740

export ALPHA=$(pmc config --get pubkey --file provider/alpha/.pmc/config)
export BETA=$(pmc config --get pubkey --file provider/beta/.pmc/config)
export GAMMA=$(pmc config --get pubkey --file provider/gamma/.pmc/config)
export DELTA=$(pmc config --get pubkey --file provider/delta/.pmc/config)


#### Manual setup ####
### t1s1/t1c1/[node0,node1]
## alpha>
# pmc cluster add -n t1s1 --pubkeys $ALPHA,$BETA -g SYSTEM_P
# pmc container add -n t1c1 -c t1s1 --pubkeys $ALPHA,$BETA --consensus-threshold 1
# pmc node update --pubkey $NODE0 --cluster-units 10 --cluster t1s1
## beta>
# pmc node update --pubkey $NODE1 --cluster-units 10 --cluster t1s1
### t1s2/t1c2/[node2,node3]
## gamma>
# pmc cluster add -n t1s2 --pubkeys $GAMMA,$DELTA -g SYSTEM_P
# pmc container add -n t1c2 -c t1s2 --pubkeys $ALPHA,$GAMMA,$DELTA --consensus-threshold 1
# pmc voterset update -vs container_t1c2_deployer --add-member $ALPHA
# pmc node update --pubkey $NODE2 --cluster-units 10 --cluster t1s2
## delta>
# pmc node update --pubkey $NODE3 --cluster-units 10 --cluster t1s2
### END of Manual setup ####

max_blocks=30000
brid0=$(curl -s https://node0.devnet2.chromia.dev:7740/brid/iid_0)
echo "chain0 brid: $brid0"
# cluster_anchoring_t1s1
cac1=F2E794CCFB9D44B7E0CEEE37184CDB5D2B09E539234C24F1F5355181220F9624
# cluster_anchoring_t1s2
cac2=933BA0EC2196A177CF344B61838B30DDD3A93E7F0A5AD3EA1773E2A1894AF8CD


echo "deploying blockchain: dapp/build/$@.xml"
brid=$(pmc blockchain add -bc ./dapp/build/$@.xml -c t1c1 -n cities$@ -q)
echo "brid: $brid"

echo "waiting 10 sec"
sleep 20s

# making sure chain is RUNNING
while true; do
  state=$(chr query --api-url $API0 -brid $brid0 nm_get_blockchain_state -- blockchain_rid="$brid")
  # state=$(pmc blockchains -ii | jq '.[] | select(.Rid=="'$brid'").State')
  if [ "$state" = '"RUNNING"' ]; then
    echo "blockchain is $state"
    break;
  else
    echo "blockchain is $state, waiting for RUNNING"
    sleep 5s
  fi
done

# height
while true; do
  h=$(curl -s "$API0/blockchain/$brid/height" | jq '.blockHeight')
  echo "height: $h"

  if [ "$h" = "null" ]; then
    sleep 5s
    continue
  fi

  if [ $h -gt $max_blocks ]; then
    break
  else
    sleep 5s
  fi
done

# pausing
echo "pausing blockchain"
pmc blockchain stop -brid $brid

# making sure chain is PAUSED
while true; do
  state=$(chr query --api-url $API0 -brid $brid0 nm_get_blockchain_state -- blockchain_rid="$brid")
  # state=$(pmc blockchains -ii | jq '.[] | select(.Rid=="'$brid'").State')
  if [ "$state" = '"PAUSED"' ]; then
    echo "blockchain is $state"
    break;
  else
    echo "blockchain is $state, waiting for PAUSED"
    sleep 5s
  fi
done

# fetching the last anchored height
cac1_last_anchored_height=$(curl -s "$API0/query/$cac1?type=get_last_anchored_block&blockchain_rid=$brid" | jq '.block_height')
echo "last anchored height on cac1: $cac1_last_anchored_height"

echo "starting moving"
pmc blockchain move -brid $brid -dc t1c2
echo "finishing moving at $cac1_last_anchored_height"
pmc blockchain finish-moving -brid $brid --final-height $cac1_last_anchored_height

echo "monitoring sync"
while true; do
  h1=$(curl -s "$API0/blockchain/$brid/height" | jq '.blockHeight')
  h2=$(curl -s "$API2/blockchain/$brid/height" | jq '.blockHeight')
  echo "node0 height: $h1, node2 height: $h2"
  cac2_last_anchored_height=$(curl -s "$API2/query/$cac2?type=get_last_anchored_block&blockchain_rid=$brid" | jq '.block_height')
  echo "cac1 anchored height: $cac1_last_anchored_height, cac2 anchored height: $cac2_last_anchored_height"

  if [ $(($cac1_last_anchored_height + 1)) = $h2 ]; then
    break;
  else
    sleep 5s
  fi
done

# resuming
echo "resuming blockchain"
pmc blockchain start -brid $brid

# making sure chain is RUNNING
while true; do
  state=$(chr query --api-url $API0 -brid $brid0 nm_get_blockchain_state -- blockchain_rid="$brid")
  # state=$(pmc blockchains -ii | jq '.[] | select(.Rid=="'$brid'").State')
  if [ "$state" = '"RUNNING"' ]; then
    echo "blockchain is $state"
    break;
  else
    echo "blockchain is $state, waiting for RUNNING"
    sleep 5s
  fi
done

# making sure 5 new blocks are anchored
echo "wating until 5 new blocks are anchored"
while true; do
  cac2_last_anchored_height=$(curl -s "$API2/query/$cac2?type=get_last_anchored_block&blockchain_rid=$brid" | jq '.block_height')
  echo "cac1 anchored height: $cac1_last_anchored_height, cac2 anchored height: $cac2_last_anchored_height"

  if [ "$cac2_last_anchored_height" = "null" ]; then
    sleep 5s
    continue
  fi

  if [ $(($cac2_last_anchored_height - 5)) -gt $cac1_last_anchored_height ]; then
    echo "SUCCESS"
    break;
  else
    sleep 5s
  fi
done

