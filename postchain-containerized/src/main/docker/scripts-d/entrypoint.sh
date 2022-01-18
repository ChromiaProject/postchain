#!/bin/sh
# Copyright (c) 2017 ChromaWay Inc. See README for license information.

set -eu
. ./env.sh

echo "Configuring and starting postgres"
bash postgres-entrypoint.sh postgres

# Launching a node
echo "Launching subnode"
sh ./run.sh
