#!/bin/sh

set -eu

# Wiping db if required
# [ $1 = "WIPE_DB" ] || [ $WIPE_DB = "true" ]

ALREADY_INITED="ALREADY_INITED"

if [ ! -e $ALREADY_INITED ]
then

#  Debug
  echo "file of /opt/chromaway/postchain-subnode"
  files=`ls /opt/chromaway/postchain-subnode`
  for f in $files
  do
    echo $f
  done

#  Debug
  echo "file of /opt/chromaway/postchain-subnode/rte"
  files2=`ls /opt/chromaway/postchain-subnode/rte`
  for f in $files2
  do
    echo $f
  done

# Debug
  echo "node-config.properties content:"
  cat rte/node-config.properties
  echo "END node-config.properties content:"

	echo "Deleting the database..."
	postchain-node/postchain.sh wipe-db -nc rte/node-config.properties

#  echo "Adding my peer-infos..."
#  postchain-node/postchain.sh peerinfo-add -nc rte/node-config.properties -h $NODE_HOST -p $NODE_PORT -pk $NODE_PUBKEY
#  postchain-node/postchain.sh peerinfo-add -nc rte/node-config.properties -h $NODE_HOST2 -p $NODE_PORT2 -pk $NODE_PUBKEY2

  touch $ALREADY_INITED

fi

# Launching a node
exec postchain-node/postchain.sh run-node-auto -d rte

