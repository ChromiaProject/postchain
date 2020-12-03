#!/bin/sh

#set -eu

docker rm postchain-subnode-03-57-chain100
docker rmi chromaway/postchain-subnode:3.2.1
