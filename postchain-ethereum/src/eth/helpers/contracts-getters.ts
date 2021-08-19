import { getEthersSigners } from './contracts-helpers';

export const getFirstSigner = async () => (await getEthersSigners())[0];