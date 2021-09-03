#!/bin/sh
# Copyright (c) 2017 ChromaWay Inc. See README for license information.

set -eu
. ./env.sh

echo "Starting postgres"
bash postgres-entrypoint.sh postgres &

# Wait for postgres to finnish before launching postchain
until netstat -tulpan |grep 5432|grep LISTEN ; do sleep 0.1; done > /dev/null 2>&1

# Launching a node
echo "Launching subnode"
sh ./run.sh
