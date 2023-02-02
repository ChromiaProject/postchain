# Retries in Postchain client

## Responses

The postchain client classify responses into these categories:

### Success

* HTTP 200 OK

### Client failure

* HTTP 400 Bad Request
* HTTP 404 Not Found
* HTTP 409 Conflict

### Server failure

* Inability to resolve hostname in DNS
* HTTP 500 Internal Server Error
* HTTP 503 Service Unavailable

### Transient server failure

* Connection refused
* Timeout
* All other responses

## Request strategies

### SINGLE

Only uses a single node (selected randomly), will return failure on both client failures and server failures,
retries the same node on transient server failures.

### ABORT_ON_ERROR

Uses all nodes in the cluster (randomly ordered), will return failure on client failures, try next node on server
failure, reties the same node on transient server failures.

### TRY_NEXT_ON_ERROR

Uses all nodes in the cluster (randomly ordered), will try next node on both client and server failure,
reties the same node on transient server failures.

### QUERY_MAJORITY

Queries all nodes in the cluster in parallel. Compare successful responses from different nodes, and tries to establish
a BFT majority of agreeing nodes, fails with `NodesDisagree` exception if that's not possible. Will not retry the same
node on failure.
