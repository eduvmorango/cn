# How to run

Run `sbt`

for running the application: `project plaform; run`
for running the tests: `project plaform; test`

There is an available postman collection containing the endpoints used to interact with the application

# Description

This project was developed using `Scala 3.4.2` and the Typelevel Ecosystem (using concrete IO instead of tagless), the initial plan was defining the
project architecture in three modules:
  * `core` the API and SPI (which allows to inject different implementations of a Ledger and other components)
  * `testKit` a toolkit containing the foundations for running the tests (not implemented)
  * `platform` the implementation of a HTTP API, services, codecs and the responsible for wiring the implementations of SPI (to
 make stuff straightforward the SPIs are implementend inside this module)

# Blocks

The blocks are a simple chained list which increases his index monotonically, all instanced ledgers will have a genesis
block containing a transaction which increases the Ledger address `100`. The transactions are encoded as lists, I've
made a shallow search and it seems people programming blockchain systems usually prefer `MerkleTrees` so this is one
of the first refactoring I would like to do before putting this application in production.

# Transactions

The transactions are based on a more primitive version of Bitcoin, so it's using the concept of UTXO. In this
application we interact with transactions doing `TransactionRequests` for single and batched transactions, these
requests generate single Transaction containing the available UTXOs at processing time as inputs and generating
new UTXOs as outputs, for batched transactions the application merges the transactions from the same source, so it's
possibly having a lot of inputs (always bound to the source address) generating UTXOs to multiple addresses.
The transactions are processed in batches of 4, so if there are 5 valid transactions requested in the same batch it will
generate two different blocks concurrently, if some transaction passes the initial validation and loses the block race,
the processing will be retried and the transaction will be ignored if it becomes invalid. The nonce goals are encoded
through the initial validations and when the ledger tries to create a new block, while isn't being used to avoid
duplications it can be used to check the blocks consistency. It's not possible to execute duplicated transactions through
the batch endpoints but there's a spec validating it into the tests.

# Addresses

Are generated through a endpoint, it returnsa new key pair, and uses the base58 encoding in the public key to generate
the unique address, the signatures are validate using a few rules
  * The address is derivable from the Public Key?
  * The public and private key are a pair?


# Balance

Are the sum of available UTXOs from an arbitrary Address, if the address never had any transactions it will generate a
404 instead of balance.

# SingleNodeLedger

Contains a concurrent `cats.data.Queue` used as the `mempool` this receive valid transactions at the submission time,
which can be ignored if the new ledger state make it invalid, the processing is happening in parallel and is hardcoded
in Chunks of `4`, so it's possible to simulate two blocks racing to be the next one, and the loser block retrying using
the new state to revalidate the transactions which were valid in the submission moment.

The initial goal was having the `Ledger` as a SPI, but I think I need to spend more time refactoring and understanding
the domain to reach a definitive SPI interface.


I think there are probably a few cases where is possible having inconsistencies, this is my first time thinking about
UTXOs while doesn't seems much more complex than the traditional models for ledgers I've used before, there's no
coverage enough, I believe the inconsistencies could be found using properties tests, and I would like to have a
specialized domain on it, since there's duplication, so in this way would be possible having more coverage and
consistency. About the HTTP application, it seems immature to me, I think more understanding of the domain would lead to
better error typing (which was neglected) and, consequently a better route organization. Other improvements are
related to logging and context propagation.elated to logging and context propagation.
