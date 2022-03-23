import { useWeb3React } from "@web3-react/core";
import { useState, useEffect } from "react";

import { injected } from "./connectors";

export function useEagerConnect() {
  const { activate, active } = useWeb3React();

  const [tried, setTried] = useState(false);

  useEffect(() => {
    injected.isAuthorized().then((isAuthorized: boolean) => {
      if (isAuthorized) {
        activate(injected, undefined, true).catch(() => {
          setTried(true);
        });
      } else {
        setTried(true);
      }
    });
  }, []); // intentionally only running on mount (make sure it's only mounted once :))

  // if the connection worked, wait until we get confirmation of that to flip the flag
  useEffect(() => {
    if (!tried && active) {
      setTried(true);
    }
  }, [tried, active]);

  return tried;
}

export function useInactiveListener(suppress: boolean = false) {
  const { active, error, activate } = useWeb3React();

  useEffect((): any => {
    if (window.ethereum && window.ethereum.on && !active && !error && !suppress) {
      const handleConnect = () => {
        activate(injected);
      };
      const handleChainChanged = (chainId: string | number) => {
        activate(injected);
      };
      const handleAccountsChanged = (accounts: string[]) => {
        if (accounts.length > 0) {
          activate(injected);
        }
      };
      const handleNetworkChanged = (networkId: string | number) => {
        activate(injected);
      };

      window.ethereum.on("connect", handleConnect);
      window.ethereum.on("chainChanged", handleChainChanged);
      window.ethereum.on("accountsChanged", handleAccountsChanged);
      window.ethereum.on("networkChanged", handleNetworkChanged);

      return () => {
        if (window.ethereum.removeListener) {
          window.ethereum.removeListener("connect", handleConnect);
          window.ethereum.removeListener("chainChanged", handleChainChanged);
          window.ethereum.removeListener("accountsChanged", handleAccountsChanged);
          window.ethereum.removeListener("networkChanged", handleNetworkChanged);
        }
      };
    }
  }, [active, error, suppress, activate]);
}