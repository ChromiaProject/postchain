#!/bin/sh
# Copyright (c) 2022 ChromaWay Inc. See README for license information.

set -eu

FROM_BRANCH=$1

mvn clean -B gitflow:hotfix-start \
  -DfetchRemote=false \
  -DpushRemote=false \
  -DfromBranch=$FROM_BRANCH

HOTFIX_BRANCH=$(git branch --show-current)
mvn clean install -DskipTests

mvn -B gitflow:hotfix-finish \
  -DfetchRemote=false \
  -DskipTestProject=true \
  -DversionDigitToIncrement=2 \
  -DhotfixBranch=$HOTFIX_BRANCH \
  -DpushRemote=false

git branch -D $HOTFIX_BRANCH
