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
