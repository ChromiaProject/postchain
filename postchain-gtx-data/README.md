# Postchain-gtx-data

Postchain-gtx-data holds data-transfer types of the Postchain-specific format "GTX". 

## GTX
GTX is a protocol, used for many things, for example transferring transactions.
GTX is encoded into the base protocol GTV.

## GTX Data
If you plan to create a Postchain transaction and send it to a Postchain server, 
you don't need the full GTX implementation. All you need is GTX Data (this module). 
GTX Data knows how to encode a transaction.

## GTV only?
It's not recommended to use GTV directly, event though it is possible to construct various Postchain
types directly into GTV.
