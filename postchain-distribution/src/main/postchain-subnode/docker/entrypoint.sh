#!/bin/sh
# Copyright (c) 2017 ChromaWay Inc. See README for license information.

set -eu

# Seting env-s
#export NODE_HOST=${ENV_NODE_HOST:-127.0.0.1}
#export NODE_PORT=${ENV_NODE_PORT:-9871}
#export NODE_PUBKEY=${ENV_NODE_PUBKEY:-0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57}
#export NODE_HOST2=${ENV_NODE_HOST2:-127.0.0.1}
#export NODE_PORT2=${ENV_NODE_PORT2:-9872}
#export NODE_PUBKEY2=${ENV_NODE_PUBKEY2:-031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F}

export DAPP_NAME=dapp

# just sh
#sh

# Deploying chain-zero dapp
if [ $BUILD_RELL_DAPP = "true" ]
then
	sh ./deploy.sh
fi

# Launching a node
sh ./run.sh
