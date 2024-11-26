# EBFT Versioning

The EBFT protocol is versioned and nodes will negotiate for which version to use.

## Version alignment

Upon a new connection both nodes assume that the other node is on version 1. If a node is on a higher version than 1 it
will send an `EbftVersion` message to notify the peer that it supports a higher version. As soon as a node receives a
version message from another node it will start to decode received messages with that version and also send new messages
using that version (if it has support for it).
