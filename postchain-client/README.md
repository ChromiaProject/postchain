# Postchain-client

Postchain-client is a kotlin client for making queries and transactions from an application

## Setup

To depend on this jar file you need to add this to your Maven "pom.xml" (currently it's not on Maven central, so this
requires Gitlab access):

```xml
<dependency>
    <groupId>net.postchain</groupId>
    <artifactId>postchain-client</artifactId>
</dependency>
```
```xml
<repository>
    <id>postchain</id>
    <name>Postchain GitLab Registry</name>
    <url>https://gitlab.com/api/v4/projects/32294340/packages/maven</url>
</repository>
```

## Usage

To generate a client one could go about it like this:

```kotlin
  import net.postchain.base.SECP256K1CryptoSystem
  import net.postchain.client.core.DefaultSigner
  import net.postchain.client.core.PostchainClient
  import net.postchain.client.core.PostchainClientFactory
  import net.postchain.client.core.TransactionStatus

  val cryptoSystem = SECP256K1CryptoSystem()
  val bcRid = ... // The blockchain RID of the chain (can be found in the logs when P.C. server starts) 
  val pubKey0 = ... // This must be a real pub key
  val privKey0 = ... // This must be a real priv key
  val sigMaker0 = cryptoSystem.buildSigMaker(pubKey0, privKey0)
  val defaultSigner = DefaultSigner(sigMaker0, pubKey0)
  val resolver = PostchainClientFactory.makeSimpleNodeResolver("http://127.0.0.1:7740") // Running P.C. server on localhost
  val psClient = PostchainClientFactory.getClient(resolver, bcRid, defaultSigner)

  // Build the TX 
  val txBuilder = psClient.makeTransaction()
  txBuilder.addOperation("nop", arrayOf()) // Operation "nop" with on arguments
  txBuilder.sign(sigMaker0) // Sign it

  // Synchronous call = waits for result
  val result = txBuilder.postSync(ConfirmationLevel.NO_WAIT)
  when (result.status) {
      TransactionStatus.CONFIRMED -> // TX has been put into the TX queue of the Postchain server
      TransactionStatus.REJECTED  -> // TX most likely wrong number of args
      else ->  // Investigate
  }
```
... see PostChainClientTest in the devtools Maven module for more examples.

