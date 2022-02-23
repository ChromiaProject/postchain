#!/bin/bash
set -eu

D=`dirname ${BASH_SOURCE[0]}`

${RELL_JAVA:-java} -cp "$D/bin/*" net.postchain.rell.RellCLIKt "$@"
