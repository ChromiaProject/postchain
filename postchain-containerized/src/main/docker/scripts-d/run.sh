#!/bin/sh

set -eu

# Wiping db if required
# [ $1 = "WIPE_DB" ] || [ $WIPE_DB = "true" ]

date '+%Y %b %d %H:%M'

ALREADY_INITED=$TARGET_DIR/.initialized

if [ ! -e $ALREADY_INITED ]
then
  echo "pwd:"
  pwd

  echo -e "\nfiles of /opt/chromaway/postchain"
  files=`ls /opt/chromaway/postchain`
  for f in $files
  do
    echo " * $f"
  done

  echo -e "\nfiles of /opt/chromaway/postchain/target"
  files2=`ls /opt/chromaway/postchain/target`
  for f in $files2
  do
    echo " * $f"
  done

  echo -e "\nfiles of /opt/chromaway/postchain/scripts"
  files3=`ls /opt/chromaway/postchain/scripts`
  for f in $files3
  do
    echo " * $f"
  done
  echo -e "\n"

	echo "Deleting the database..."
	scripts/postchain.sh wipe-db -nc $TARGET_DIR/node-config.properties

  echo "Adding my peer-info..."
  . ./$TARGET_DIR/env-peers.sh
  scripts/postchain.sh peerinfo-add -nc $TARGET_DIR/node-config.properties -h $NODE_HOST -p $NODE_PORT -pk $NODE_PUBKEY

  touch $ALREADY_INITED

fi

# Launching a node
echo "Launching postchain node ..."
exec scripts/postchain.sh run-node-auto -d $TARGET_DIR

