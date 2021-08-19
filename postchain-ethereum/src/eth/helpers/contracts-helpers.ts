import { Contract, Signer, utils, ethers, BigNumberish } from 'ethers';
import { getDb, DRE, waitForTx } from './misc-utils';
import { verifyEtherscanContract } from './etherscan-verification';
import { getDefenderRelaySigner, usingDefender } from './defender-utils';

export const getEthersSigners = async (): Promise<Signer[]> => {
  const ethersSigners = await Promise.all(await DRE.ethers.getSigners());

  if (usingDefender()) {
    const [, ...users] = ethersSigners;
    return [await getDefenderRelaySigner(), ...users];
  }
  return ethersSigners;
};

export const registerContractInJsonDb = async (contractId: string, contractInstance: Contract) => {
    const currentNetwork = DRE.network.name;
    const FORK = process.env.FORK;
    if (FORK || (currentNetwork !== 'hardhat' && !currentNetwork.includes('coverage'))) {
      console.log(`*** ${contractId} ***\n`);
      console.log(`Network: ${currentNetwork}`);
      console.log(`tx: ${contractInstance.deployTransaction.hash}`);
      console.log(`contract address: ${contractInstance.address}`);
      console.log(`deployer address: ${contractInstance.deployTransaction.from}`);
      console.log(`gas price: ${contractInstance.deployTransaction.gasPrice}`);
      console.log(`gas used: ${contractInstance.deployTransaction.gasLimit}`);
      console.log(`\n******`);
      console.log();
    }
  
    await getDb()
      .set(`${contractId}.${currentNetwork}`, {
        address: contractInstance.address,
        deployer: contractInstance.deployTransaction.from,
      })
      .write();
  };

export const withSaveAndVerify = async <ContractType extends Contract>(
    instance: ContractType,
    id: string,
    args: (string | string[])[],
    verify?: boolean
  ): Promise<ContractType> => {
    await waitForTx(instance.deployTransaction);
    await registerContractInJsonDb(id, instance);
    if (verify) {
      await verifyContract(id, instance, args);
    }
    return instance;
  };

  export const verifyContract = async (
    id: string,
    instance: Contract,
    args: (string | string[])[]
  ) => {
    await verifyEtherscanContract(instance.address, args);
    return instance;
  };