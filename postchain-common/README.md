# Postchain-common

Postchain-common holds essential types other modules and applications might need (for example postchain-client).

Transaction
This includes interfaces regarding transactions


Postchain-vitals holds types that correspond to low level domain model,
i.e. transactions and block. Postchain-vitals differs from Postchain-common in
that it knows about GTV, and can use it to represent Postchain specific data. 