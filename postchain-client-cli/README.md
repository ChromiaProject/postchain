# Postchain-clien-cli

This module is a CLI for sending transactions to a postchain node

## Setup

Configure the client by creating a file which points to a running node

```properties
api-url=http://<host>:<port>
blockchain-rid=
privkey=
pubkey=
```

## Usage

### Post transaction

Post a transaction using the `post-tx` command. See help:
```shell
post-tx --help
```

### Post query

Make a query using the `query` command. This command accepts key-value pairs or a formatted dict:
```shell
query foo key1=value1 key2=value2
query foo {key1=value1,key2=value2}
```
