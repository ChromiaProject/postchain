#!/bin/sh

set -eu

docker build . -t chromaway/postchain-subnode:3.2.1

#################
#
# The problem under WSL v1:
#   Docker needs: /d/...
#   WSL operates with: /mnt/d/...
# Fix:
#   Make link: sudo mount --bind /mnt/d /d
#
#################

docker run \
--name postchain-subnode \
-p 7742:7741 \
-v /d/Home/Dev/ChromaWay/postchain2/postchain-distribution/src/main/postchain-subnode/docker/rte:/opt/chromaway/postchain-subnode/rte \
-it chromaway/postchain-subnode:3.2.1

