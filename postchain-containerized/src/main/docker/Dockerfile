# Copyright (c) 2019 ChromaWay Inc. See README for license information.
# Postchain Node Docker file with postgres

FROM postgres:11-alpine

# Install jdk8, bash, curl
RUN apk update && apk add openjdk8 bash curl

ENV GOSU_VERSION 1.13
RUN set -eux; \
  \
  apk add --no-cache --virtual .gosu-deps \
  ca-certificates \
  dpkg \
  gnupg \
  ; \
  \
  dpkgArch="$(dpkg --print-architecture | awk -F- '{ print $NF }')"; \
  wget -O /usr/local/bin/gosu "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch"; \
  wget -O /usr/local/bin/gosu.asc "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch.asc"; \
  \
  # verify the signature
  export GNUPGHOME="$(mktemp -d)"; \
  gpg --batch --keyserver hkps://keys.openpgp.org --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4; \
  gpg --batch --verify /usr/local/bin/gosu.asc /usr/local/bin/gosu; \
  command -v gpgconf && gpgconf --kill all || :; \
  rm -rf "$GNUPGHOME" /usr/local/bin/gosu.asc; \
  \
  # clean up fetch dependencies
  apk del --no-network .gosu-deps; \
  \
  chmod +x /usr/local/bin/gosu; \
  # verify that the binary works
  gosu --version; \
  gosu nobody true

ENV POSTGRES_DB=postchain
ENV POSTGRES_USER=postchain
ENV POSTGRES_PASSWORD=postchain


# Scripts
ADD scripts /opt/chromaway/postchain/scripts/
ADD ./scripts-d/* /opt/chromaway/postchain/
ADD env.sh /opt/chromaway/postchain/env.sh
ADD ./dependencies/ /opt/chromaway/postchain/scripts/bin/


ENV BUILD_RELL_DAPP false

WORKDIR /opt/chromaway/postchain
ENTRYPOINT ["sh", "/opt/chromaway/postchain/entrypoint.sh"]

STOPSIGNAL SIGINT

CMD [ "postgres" ]