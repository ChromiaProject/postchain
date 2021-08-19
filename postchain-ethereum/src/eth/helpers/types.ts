import BigNumber from 'bignumber.js';

export interface SymbolMap<T> {
  [symbol: string]: T;
}

export type eNetwork = eEthereumNetwork

export enum eEthereumNetwork {
  buidlerevm = 'buidlerevm',
  kovan = 'kovan',
  ropsten = 'ropsten',
  main = 'main',
  coverage = 'coverage',
  hardhat = 'hardhat',
  tenderlyMain = 'tenderlyMain',
}

export enum TokenContractId {
    CHR = 'CHR',
}

export enum eContractid {
    TestToken = 'TestToken',
}

