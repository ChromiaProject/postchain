# Token bridge smart contract

Uses

- [Hardhat](https://github.com/nomiclabs/hardhat): compile and run the smart contracts on a local development network
- [TypeChain](https://github.com/ethereum-ts/TypeChain): generate TypeScript types for smart contracts
- [Ethers](https://github.com/ethers-io/ethers.js/): renowned Ethereum library and wallet implementation
- [Waffle](https://github.com/EthWorks/Waffle): tooling for writing comprehensive smart contract tests
- [Solhint](https://github.com/protofire/solhint): linter
- [Prettier Plugin Solidity](https://github.com/prettier-solidity/prettier-plugin-solidity): code formatter

## Usage

### Pre Requisites

Before running any command, make sure to install dependencies:

```sh
$ yarn install
```


### Clean

```sh
$ yarn clean
```

### Compile

Compile the smart contracts with Hardhat:

```sh
$ yarn compile
```

### Test

Run the Mocha tests:

```sh
$ yarn test
```

Run test with gas report

```sh
$ REPORT_GAS=true yarn test
```

Run test with solidity coverage report

```sh
$ yarn coverage
```

### Deploy token bridge contract to a network (requires Mnemonic, infura API and Etherscan API key)

```sh
$ yarn deploy --network rinkeby --verify --app 0x659E4A3726275EDFD125F52338ECE0D54D15BD99,0x1A642F0E3C3AF545E7ACBD38B07251B3990914F1,0x75E20828B343D1FE37FAE469AB698E19C17F20B5
```

### Run simple dapp on local

Create `.env` file to config your dapp, then run below command to start the dapp on local

```
$ yarn start
```
Voila! Now you can access to `http://localhost:3000/` to use the dapp to interact with token bridge smart contract

### Build the DApp for production deployment

```
$ yarn build

```

The build folder is ready to be deployed.
We may serve it with a static server:

```
$ yarn global add serve
$ serve -s build
```

Find out more about deployment here:
    https://cra.link/deployment

### Added plugins

- Gas reporter [hardhat-gas-reporter](https://hardhat.org/plugins/hardhat-gas-reporter.html)
- Etherscan [hardhat-etherscan](https://hardhat.org/plugins/nomiclabs-hardhat-etherscan.html)

## Upgrade token bridge contract

### Prepare

Run below task to prepare upgrade token bridge smart contract

```sh
yarn prepare-upgrade --network rinkeby --verify --address PROXY_ADDRESS
```

### Upgrade

```sh
$ yarn upgrade-contract --network rinkeby --address PROXY_ADDRESS
```
