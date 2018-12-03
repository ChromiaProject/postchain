#!/bin/sh
# Copyright (c) 2017 ChromaWay Inc. See README for license information.

set -e

POSTCHAIN_CP=/opt/chromaway/postchain-2.4.0-SNAPSHOT-jwd.jar:$ENV_APP_CLASSPATH
NODE_CONFIG=${ENV_NODE_CONFIG:-/postchain-node/node-config.properties}
CHAIN_ID=${ENV_CHAIN_ID:-1}

# Workaround to start 'postchain' docker container after 'postgres' one get ready
# TODO: Use 'wait-for-it' tool: https://docs.docker.com/compose/startup-order/
sleep 10

# Add Blockchain defined in blockchain config file
java -cp $POSTCHAIN_CP net.postchain.AppKt add-blockchain \
	-nc $NODE_CONFIG \
	-brid $ENV_BLOCKCHAIN_RID \
	-cid $CHAIN_ID \
    -bc $ENV_BLOCKCHAIN_CONFIG

# Launch Postchain node
java -cp $POSTCHAIN_CP net.postchain.AppKt run-node \
    -nc $NODE_CONFIG \
    -cid $CHAIN_ID
