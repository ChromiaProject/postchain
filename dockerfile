FROM java:8
WORKDIR /
ADD postchain-base/target/postchain-base-2.4.2-jar-with-dependencies.jar postchain-base-2.4.2-jar-with-dependencies.jar
ADD postchain-base/src/main/resources/config/common.properties common.properties
ADD postchain-base/src/main/resources/config/config.0.properties config.0.properties
ADD postchain-base/src/main/resources/blockchain-config/configration1.xml configration1.xml
RUN /bin/sh
EXPOSE 8080
CMD java -jar postchain-base-2.4.2-jar-with-dependencies.jar add-blockchain --node-config config.0.properties --chain-id 0 --blockchain-rid 78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3 --blockchain-config configration1.xml --force
CMD java -jar postchain-base-2.4.2-jar-with-dependencies.jar run-node -cid 0
