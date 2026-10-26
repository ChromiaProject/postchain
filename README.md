# Postchain (JVM edition)

Postchain is a blockchain framework designed primarily for consortium databases.

Technical information & user guide can be found [here](doc).

## API compatibility

The public API of `postchain-common`, `postchain-gtv`, `postchain-gtv-yaml`, `postchain-gtx-data`, `postchain-spi` is tracked with `kotlin-bcv-maven-plugin`. Each module records its API in `<module>/api/<module>.api`, and the build (`verify` phase) fails if the compiled API differs from that file.

To accept an intended API change, compile the module, regenerate the file and commit it:

```shell
mvn -pl <module> -am -DskipTests clean compile com.chromaway:kotlin-bcv-maven-plugin:0.2.0:dump
```

To run only the check (needs compiled classes):

```shell
mvn -pl <module> com.chromaway:kotlin-bcv-maven-plugin:0.2.0:check
```

## Copyright & License information

Copyright (c) 2017–2023 ChromaWay AB. All rights reserved.

This software can be used either under the terms of commercial license
obtained from ChromaWay AB, or, alternatively, under the terms
of the GNU General Public License with additional linking exceptions.
See file LICENSE for details.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
