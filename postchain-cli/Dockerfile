# Copyright (c) 2022 ChromaWay Inc. See README for license information.
FROM gcr.io/distroless/java17-debian11:latest@sha256:9370a7ed01d00d58f38d51e4b89edce480bfbd7a759d78363036bf406e7239d8

ARG postchain_dir=/opt/chromaway/postchain
COPY target/lib $postchain_dir/lib
COPY target/*.jar $postchain_dir/lib
COPY src/main/resources/log4j2.yml $postchain_dir/log/log4j2.yml

ENV LOG4J_CONFIGURATION_FILE=/opt/chromaway/postchain/log/log4j2.yml

WORKDIR $postchain_dir
ENTRYPOINT ["/usr/bin/java" \
, "-classpath", "/opt/chromaway/postchain/lib/*" \
, "net.postchain.AppKt" ]
