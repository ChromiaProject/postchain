# Postchain admin-client

The admin-client is a client for communicating over rpc to a postchain node running in server mode.

## Setup

### TLS

When running postchain in production, we recommend encrypting the communication. This is done by enabling it on the postchain server and specifying the certificate file in the admin client when running commands. This is typically done using environment variables.

### Environment

Each command has its own environment variables that can be used instead of specifying them from CLI. Below listed variables are common for all commands (except blockchain config which is included for convenience).
Use `--help` on a command to get more information.

| Variable                    | Default       | Description                                               |
|:----------------------------|:--------------|:----------------------------------------------------------|
| POSTCHAIN_TARGET            |               | host:port for the postchain server                        |
| POSTCHAIN_BLOCKCHAIN_CONFIG | /config/0.xml | Convenient default value used when adding a configuration |   
| POSTCHAIN_TLS               | false         | Enables TLS encryption                                    |   
| POSTCHAIN_CERTIFICATE       |               | Path to mounted certificate file                          |   

## Example usage

Say that we have a Postchain server running on localhost exposing port 50051. Then we can list peer information by:

```commandline
docker run -it --rm registry.gitlab.com/chromaway/postchain/chromaway/postchain-admin-client:3.8.0-SNAPSHOT list-peers -t localhost:50051
or
docker run -it --rm -e POSTCHAIN_TARGET=localhost:50051 registry.gitlab.com/chromaway/postchain/chromaway/postchain-admin-client:3.8.0-SNAPSHOT list-peers
```

To initialize a new blockchain, we need to mount the configuration file:

```commandline
docker run -it --rm \
    -e POSTCHAIN_TARGET=host.docker.internal:50051 \
    -v <path-to-config>:/config \
    registry.gitlab.com/chromaway/postchain/chromaway/postchain-admin-client:3.8.0-SNAPSHOT \
    initialize-blockchain --chain-id 100
```
