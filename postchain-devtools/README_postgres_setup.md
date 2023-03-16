# As a developer you need PostgreSQL

## PostgreSQL setup for Postchain dev

To develop Postchain you need a database, and this guide shows how to setup a default user with a default password. 
In the code below we assume you are running Linux, Ubuntu. We begin with installing the Postgres Database (if you don't have it already):

```bash
  1 sudo apt update
  2 sudo apt install postgresql
  3
```

Once the Postgres software has been installed we must create a "postchain" user and db. Before we have it we must use the "postgres" user to login, like this:

```bash
  4 sudo -u postgres psql  
  5
```

When we are inside we create the standard developer's default setup. "<YOUR_NAME>" below is the name of your Linux user.

```sql
  6 postgres=# CREATE DATABASE postchain WITH TEMPLATE = template0 LC_COLLATE = 'C.UTF-8' LC_CTYPE = 'C.UTF-8' ENCODING 'UTF-8';
  7  CREATE DATABASE
  8 postgres=# CREATE USER postchain WITH ENCRYPTED PASSWORD 'postchain';
  9  CREATE ROLE
 10 postgres=# GRANT ALL PRIVILEGES ON DATABASE postchain TO postchain;
 11  GRANT
 12 postgres=# CREATE USER <YOUR_NAME> WITH ENCRYPTED PASSWORD '<YOUR_PW>';
 13  CREATE ROLE
 14 postgres=# GRANT postchain TO <YOUR_NAME>;
 15  GRANT ROLE
 16 postgres-# \q
 17
```

Now we should be able to use operative system's user <YOUR_NAME>, but we still have to specify the DB name using the "-d" flag. No easy way to make that automatic.

```bash 
 18 psql -d postchain    
 19
 20 postgres=#  #--- IT WORKED!
 21
```

Try build Postchain with:

```bash 
 22 mvn clean install
```

## Investigate PostgreSQL test data

When running an integration test the test will usually create a new schema for 
itself based to the test's name. We can see all schemas using the ``<backslash>dn`` command 
(don't know how to make Markup show only one backslash, but whenever you see two backslash in this doc remember: there should only be one).

```bash 
postchain=> \dn
List of schemas
Name                     |   Owner   
----------------------------------------------+-----------
anchorintegrationtest0_0                     | postchain
anchorintegrationtest1_1                     | postchain
anchorintegrationtest2_2                     | postchain
blockchainconfigurationtest0_0               | postchain
blockchainenginetest0_0                      | postchain
...
```
To look at the data for one specific test we have to set Postgres to use this schema using the "set search_path to.." command:
```bash 
postchain=> set search_path to anchorintegrationtest0_0;
SET
```
We can now see the tables for this schema using "\d":
```bash 
postchain=> \d
List of relations
Schema          |                 Name                 |   Type   |   Owner   
--------------------------+--------------------------------------+----------+-----------
anchorintegrationtest0_0 | blockchain_replicas                  | table    | postchain
anchorintegrationtest0_0 | blockchains                          | table    | postchain
anchorintegrationtest0_0 | c1.blocks                            | table    | postchain
anchorintegrationtest0_0 | c1.blocks_block_iid_seq              | sequence | postchain
anchorintegrationtest0_0 | c1.configurations                    | table    | postchain
...
```
Now when we do a SELECT it will look at the data for your specific test: 
```bash 
postchain=> SELECT * FROM blockchains;
 chain_iid |                           blockchain_rid                           
-----------+--------------------------------------------------------------------
         1 | \x19a1d544fcf9ce8de44aaedd3b6803fe524730486f84bd2f62c508a25fe36f5a
         2 | \x3455dbcf67151ba27c93d2d0edfd9356dd5dae69d519c8fb94e8620e21681536
(2 rows)
```

If you want to look in the ```c1.blocks``` table you must put ``<double quotes>`` around the name 
(because of the "." in the name):
```bash 
postchain=> select block_iid, block_height from "c1.blocks";
block_iid | block_height
-----------+--------------
1 |            0
2 |            1
3 |            2
4 |            3
(4 rows)
```



## Copyright & License information

Copyright (c) 2017-2022 ChromaWay AB. All rights reserved.

This software can used either under terms of commercial license
obtained from ChromaWay AB, or, alternatively, under the terms
of the GNU General Public License with additional linking exceptions.
See file LICENSE for details.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
