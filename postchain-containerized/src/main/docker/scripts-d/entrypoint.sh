#!/bin/sh
# Copyright (c) 2017 ChromaWay Inc. See README for license information.

set -eu
. ./env.sh

echo "Starting postgres"
bash postgres-entrypoint.sh postgres &


# while [ ! -e /postgres_started ]; do
#  sleep 1
# done

# rm /postgres_started

# This is not a good solution, its just for now, so that postgres have time to finnish before postchain needs it.

sleep 10

# Launching a node
echo "Launching subnode"
sh ./run.sh
