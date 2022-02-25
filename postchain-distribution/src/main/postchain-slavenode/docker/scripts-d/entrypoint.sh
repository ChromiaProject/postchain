#!/bin/sh
# Copyright (c) 2017 ChromaWay Inc. See README for license information.

set -eu
. ./env.sh

# just sh
#sh

# Deploying chain-zero dapp
if [ $BUILD_RELL_DAPP = "true" ]
then
	sh ./deploy.sh
fi

# Launching a node
sh ./run.sh
