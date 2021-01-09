#!/bin/sh

set -eu
. ./env.sh

docker build . -t $IMAGE:$VER
