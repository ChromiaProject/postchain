#!/bin/sh
# Copyright (c) 2017 ChromaWay Inc. See README for license information.

#sh

set -eu
. ./env.sh

docker rmi -f $IMAGE:$VER
docker rmi -f $IMAGE:latest
