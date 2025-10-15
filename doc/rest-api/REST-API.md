# Postchain REST API

This document is aimed at developers of the Postchain REST API.

## API documentation

We manually maintain an OpenAPI specification here: 
`postchain-base/src/main/resources/restapi-docs/postchain-restapi.yaml`

## Path parameters

| Parameter       | Type    | Description     |
|-----------------|---------|-----------------|
| {blockchainRid} | hex     | blockchain RID  |
| {txRid}         | hex     | transaction RID |
| {blockRid}      | hex     | block RID       |
| {height}        | integer | block height    |
| {queryRid}      | hex     | async query RID |

## Query parameters

| Parameter     | Type    | Description                                                             |
|---------------|---------|-------------------------------------------------------------------------|
| limit         | integer | limit number of items to return                                         | 
| before-time   | integer | filter on time                                                          |
| after-time    | integer | filter on time                                                          | 
| before-height | integer | filter on block height                                                  |
| after-height  | integer | filter on block height                                                  |
| txs           | boolean | whether to include transactions                                         |
| tx-data       | boolean | whether to include full transaction data                                |
| exclude-empty | boolean | whether to exclude empty blocks                                         | 
| height        | integer | blockchain configuration on a specific height, or -1 for current height |  
| signer        | hex     | filter by transaction signer                                            | 
| container     | string  | specify container explicitly                                            | 
| decode-tx     | boolean | decode transaction into JSON                                            |

## Headers

It's recommended to use the Structured Header standard (RFC 9651) for any header that contains a non-trivial value, 
as currently done with `X-Query-Response-Signature`. (Ideally, this should have been done with `X-Postchain-Signature` 
also). 

### Request headers

| Header                            | Format  | Description                                 |
|-----------------------------------|---------|---------------------------------------------|
| X-Postchain-Signature             | hex:hex | authenticates the request with a public key | 
| X-Accept-Query-Response-Signature | boolean | request signed query response               | 

### Response headers

| Header                     | Format                       | Description                                                          |
|----------------------------|------------------------------|----------------------------------------------------------------------|
| X-Transaction-Timestamp    | integer                      | when the transaction was submitted                                   | 
| X-Data-Truncated           | boolean                      | indicates if the response data was truncated due to size limitations |
| X-Block-Height             | integer                      | the block height the query response was generated at                 |
| X-Query-Response-Signature | Dictionary Structured Header | node's signature of the query response                               | 

## Request and response bodies

Several endpoints can use with either JSON or binary GTV, and uses content negotiation with `Accept` and 
`Content-Type` headers to let the client choose, content type `application/octet-stream` is used for binary GTV. 
JSON is usually default for the response if there is no `Accept` header.

Endpoints dealing with blockchain configuration can use XML or binary GTV.

## Status codes and error messages

We generally try to use standard HTTP status code where it makes sense.

An unsuccessful response (status code 4xx or 5xx) also has an error message in the response body. This will be in 
binary GTV format if an `Accept: application/octet-stream` was given, JSON otherwise. The error response always has
a human-readable "error" property, and sometimes also a machine-readable "code" property.

404 Not Found is returned if the endpoint name is unrecognized or if the specified blockchain is not found.
If the blockchain is found but a subordinate resource (block, transaction, query, etc.) is not found, 
the REST API is inconsistent in either returning 404 Not Found or 200 OK with an empty response body. 

## Authentication

A few endpoints are authenticated with a public key, the `X-Postchain-Signature` request header is used for this 
purpose. 

## Define what internals that should not be exposed

Several database tables have an integer primary key column `iid`. This internal id should not be exposed through
the REST API, instead the RID should be used as the unique identifier of entities. This applies to containers, 
blockchains, blocks and transactions.
