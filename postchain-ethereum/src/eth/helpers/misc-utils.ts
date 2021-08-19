import low from 'lowdb';
import FileSync from 'lowdb/adapters/FileSync';
import { Wallet, ContractTransaction } from 'ethers';
import { HardhatRuntimeEnvironment } from 'hardhat/types';
import { BuidlerRuntimeEnvironment } from '@nomiclabs/buidler/types';

export let DRE: HardhatRuntimeEnvironment | BuidlerRuntimeEnvironment;

export const setDRE = (_DRE: HardhatRuntimeEnvironment | BuidlerRuntimeEnvironment) => {
    DRE = _DRE;
  };

  export const advanceBlock = async (timestamp: number) =>
  await DRE.ethers.provider.send('evm_mine', [timestamp]);

export const increaseTime = async (secondsToIncrease: number) => {
  await DRE.ethers.provider.send('evm_increaseTime', [secondsToIncrease]);
  await DRE.ethers.provider.send('evm_mine', []);
};

export const waitForTx = async (tx: ContractTransaction) => await tx.wait(1);

export const getDb = () => low(new FileSync('./deployed-contracts.json'));

export const impersonateAccountsHardhat = async (accounts: string[]) => {
  if (process.env.TENDERLY === 'true') {
    return;
  }
  // eslint-disable-next-line no-restricted-syntax
  for (const account of accounts) {
    // eslint-disable-next-line no-await-in-loop
    await (DRE as HardhatRuntimeEnvironment).network.provider.request({
      method: 'hardhat_impersonateAccount',
      params: [account],
    });
  }
};