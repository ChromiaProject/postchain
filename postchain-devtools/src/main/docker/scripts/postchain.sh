#!/bin/bash

set -eu

scriptdir=`dirname ${BASH_SOURCE[0]}`

#echo "${RELL_JAVA:-java} -Dlog4j.configurationFile=./scripts/bin/log4j2.yml -cp \"/maven/*\" net.postchain.AppKt $@"

#${RELL_JAVA:-java} -Dlog4j.configurationFile=./scripts/bin/log4j2.yml -cp "/maven/*" net.postchain.AppKt $@

#${RELL_JAVA:-java} -Dlog4j.configurationFile=./scripts/bin/log4j2.yml -cp "$scriptdir/bin/*" net.postchain.AppKt $@

${RELL_JAVA:-java} -Dlog4j.configurationFile=./scripts/bin/log4j2.yml -cp "$scriptdir/*" net.postchain.AppKt $@