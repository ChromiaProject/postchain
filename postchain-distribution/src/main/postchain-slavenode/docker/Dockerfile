# Copyright (c) 2019 ChromaWay Inc. See README for license information.
# Postchain Node Docker file
FROM openjdk:8-jre-alpine

# Install bash, curl
RUN apk update && apk add bash curl

# Scripts
ADD ./scripts/ /opt/chromaway/postchain/scripts/
ADD ./scripts-d/* /opt/chromaway/postchain/
ADD ./env.sh /opt/chromaway/postchain/env.sh

#EXPOSE 7741

ENV BUILD_RELL_DAPP false

WORKDIR /opt/chromaway/postchain
ENTRYPOINT ["sh", "/opt/chromaway/postchain/entrypoint.sh"]

