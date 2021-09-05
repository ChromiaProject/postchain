import { InjectedConnector } from "@web3-react/injected-connector";

export const POLLING_INTERVAL = 12000;
export const injected = new InjectedConnector({
  supportedChainIds: [4],
});