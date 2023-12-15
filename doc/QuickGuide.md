# Postchain quick user guide

Postchain is a blockchain framework designed primarily for consortium databases. Business-oriented information about it
can be found on [our site](https://chromaway.com/technology).

This guide contains a short overview of Postchain from technical and conceptual perspectives.

For more information, visit the [documentation](https://docs.chromia.com/) and for more practical hands-on examples
visit the [courses](https://learn.chromia.com/).

## Overview

Postchain is a modular framework for implementing custom blockchains. Particularly,
it's geared towards _consortium_ blockchains (also known as _permissioned_, _enterprise_,
_private_, _federated_ blockchains, and, sometimes, _distributed ledger technology_).

In a consortium blockchain, typically blocks must be approved (signed) by a majority of consortium
members. This model is also known as _proof-of-authority_, to contrast it with _proof-of-work_ and _proof-of-stake_.

Postchain has modular architecture, where different parts (such as consensus, transaction format,
crypto system) can be customized independently of each other.

The main feature which differentiates Postchain from a similar system is that it integrates with
SQL databases in a very deep way: all blockchain data is stored in an SQL database, transaction
logic can be defined in terms of SQL code (particularly, stored procedures). However, we should note
that Postchain uses SQL as a black box: it is not a database plugin, and it works with databases
such as PostgreSQL as is, without any special configuration or modification.

### Tech stack

* Programming languages: Kotlin and Java, SQL
* SQL database: PostgreSQL
* Operating systems: anything which supports Java 17; tested on Linux and Mac OS X
* Cryptosystem: SECP256k1 for signing by default, but it is customizable. SHA256 is used for hashing.

### Programming model

Custom blockchains should be programmed in [Rell](https://docs.chromia.com/category/rell-language)

## Architecture

Postchain consists of the following components:

* Core: Defines common interfaces which allow different modules to interoperate with each other.
* Base: Defines base classes which are geared towards enterprise blockchains. They can either be used as is, or
  serve as a base classes for customization.
* GTX: Defines a generic transaction format. It is a general-purpose format with several useful features, such
  as native multi-signature support. It is optional (it is easy to define a custom format), but is recommended.
  GTX also offers a way to define modules with blockchain logic which can work together with each other.
* API: REST API for submitting transactions and retrieving data.
* EBFT: Consensus protocol, based on PBFT. (Replaceable.)
* Client library/SDK: JavaScript, Kotlin, and C# SDK are available.

### GTX

Postchain GTX makes it possible to compose application from modules. Using pre-made modules can help to reduce
implementation time.

#### GTX modules

Postchain provides a convenient way to define GTX operations and organize them into modules.
Multiple modules can be composed together into a composite module.

Besides operations, modules also define queries which can later be performed using client API.

#### GTX transaction

GTX transaction format has the following features:

* Format is based on ASN.1 DER serialization (standardized by ITU-T, ISO, IEC)
* Has native support for multi-signature
* Has native support for atomic transactions

GTX transaction consists of one or more operations, and each operation is defined by its name and list of arguments.
E.g., one transaction might encode two operations:

     issue(<Alice account ID>, 1000, USD)
     transfer(<Alice account ID>, <Bob account ID>, 100, USD)

This looks similar to making function calls, so GTX operations can be understood as a kind of RPC, where a client
submits calls (operations) to be performed on server (network of Postchain nodes). GTX transaction is a batch
of such operations signed by clients which wish to perform them. Usually operation's update database, but they might
only perform checks. GTX transaction is atomic: either all operations succeed, or it fails as a whole.