import { MockContract } from 'ethereum-waffle';
import { getFirstSigner } from './contracts-getters';
import {
  eContractid,
  TokenContractId,
} from './types';

import {
  withSaveAndVerify,
  registerContractInJsonDb,
  verifyContract,
} from './contracts-helpers';

import {
  TestToken__factory,
} from '../typechain/factories/TestToken__factory';
import { TestToken } from '../typechain/TestToken';

export const deployAllMockTokens = async (verify?: boolean) => {
    const tokens: { [symbol: string]: MockContract | TestToken } = {};
  
    for (const tokenSymbol of Object.keys(TokenContractId)) {
      let decimals = '18';
  
      tokens[tokenSymbol] = await deployMintableERC20(
        [tokenSymbol, tokenSymbol, decimals],
        verify
      );
      await registerContractInJsonDb(tokenSymbol.toUpperCase(), tokens[tokenSymbol]);
    }
    return tokens;
  };

  export const deployMintableERC20 = async (
    args: [string, string, string],
    verify?: boolean
  ): Promise<TestToken> =>
    withSaveAndVerify(
      await new TestToken__factory(await getFirstSigner()).deploy(),
      eContractid.TestToken,
      args,
      verify
    );