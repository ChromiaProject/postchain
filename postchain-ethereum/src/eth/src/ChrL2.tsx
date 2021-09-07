import { formatUnits } from "@ethersproject/units";
import { useWeb3React } from "@web3-react/core";
import { BigNumber, ethers } from "ethers";
import { toast } from "react-hot-toast";
import React, { useEffect, useState } from "react";
import { useQuery } from "react-query";

import ERC20TokenArtifacts from "./artifacts/contracts/token/ERC20.sol/ERC20.json";
import ChrL2Artifacts from "./artifacts/contracts/ChrL2.sol/ChrL2.json";

import { restClient, gtxClient } from "postchain-client"

const postchainURL = process.env.REACT_APP_POSTCHAIN_URL
const blockchainRID = process.env.REACT_APP_POSTCHAIN_BRID
const rest = restClient.createRestClient(postchainURL, blockchainRID, 5);
const client = gtxClient.createClient(
    rest,
    blockchainRID,
    []
)

interface Props {
  chrL2Address: string;
  tokenAddress: string;
}

const TokenInfo = ({ tokenAddress }: { tokenAddress: string }) => {
  const { library } = useWeb3React();
  const fetchTokenInfo = async () => {
    const tokenContract = new ethers.Contract(tokenAddress, ERC20TokenArtifacts.abi, library);
    const name = await tokenContract.name();
    const symbol = await tokenContract.symbol();
    const decimals = await tokenContract.decimals();
    const totalSupply = await tokenContract.totalSupply();
    return {
      name,
      symbol,
      decimals,
      totalSupply,
    };
  };
  const { error, isLoading, data } = useQuery(["token-info", tokenAddress], fetchTokenInfo, {
    enabled: tokenAddress !== "",
  });

  if (error) return <div>failed to load</div>;
  if (isLoading) return <div>loading...</div>;

  return (
    <div className="flex flex-col">
      <button className="btn">
        {data?.name}
        <div className="ml-2 badge">{data?.symbol}</div>
        <div className="ml-2 badge badge-info">{data?.decimals}</div>
      </button>

      <div className="shadow stats">
        <div className="stat">
          <div className="stat-title">Total Supply</div>
          <div className="stat-value">{Number(formatUnits(data?.totalSupply ?? 0, data?.decimals)).toFixed(6)}</div>
        </div>
      </div>
    </div>
  );
};

const ChrL2Contract = ({ chrL2Address, tokenAddress }: Props) => {
  const { library, chainId, account } = useWeb3React();
  const [balance, setBalance] = useState(BigNumber.from(0));
  const [deposite, setDeposit] = useState(BigNumber.from(0));
  const [amount, setAmount] = useState(0);
  const [unit, setUnit] = useState(18);
  const [eventHash, setEventHash] = useState("00000000000000000000000000000000")

  const withdrawRequest = async () => {
    const signer = library.getSigner();
    try {
      let event = await client.query('get_event_merkle_proof', { "eventHash": eventHash })
      let data = JSON.parse(JSON.stringify(event))
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )

      const blockHeader = "0x" + data.blockHeader
      const blockWitness = data.blockWitness
      let sigs = new Array<string>(blockWitness.length)
      for (let i = 0; i < blockWitness.length; i++) {
        sigs[i] = "0x" + blockWitness[i].sig
      }

      const eventData = data.eventData
      const evtPosition = eventData[1]
      const evtHash = "0x" + eventData[2]
      const evtData = "0x" + eventData[3]
      const merkleProofs = data.merkleProofs
      let proofs = new Array<string>(merkleProofs.length)
      for (let i = 0; i < merkleProofs.length; i++) {
        proofs[i] = "0x" + merkleProofs[i]
      }
      const calldata = chrl2.interface.encodeFunctionData("withdraw_request", [evtData, evtHash, blockHeader, sigs, proofs, evtPosition])
      const txPrams = {
        to: chrL2Address,
        value: '0x0',
        data: calldata
      };
      const transaction = await signer.sendTransaction(txPrams);
      toast.promise(transaction.wait(), {
        loading: `Transaction submitted. Wait for confirmation...`,
        success: <b>Transaction confirmed!</b>,
        error: <b>Transaction failed!.</b>,
      });
    } catch (error) { }
  }

  const withdraw = async () => {
    const signer = library.getSigner();
    try {
      let event = await client.query('get_event_merkle_proof', { "eventHash": eventHash })
      let data = JSON.parse(JSON.stringify(event))
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )
      const eventData = data.eventData
      const evtHash = "0x" + eventData[2]
      const calldata = chrl2.interface.encodeFunctionData("withdraw", [evtHash, account])
      const txPrams = {
        to: chrL2Address,
        value: '0x0',
        data: calldata
      };
      const transaction = await signer.sendTransaction(txPrams);
      toast.promise(transaction.wait(), {
        loading: `Transaction submitted. Wait for confirmation...`,
        success: <b>Transaction confirmed!</b>,
        error: <b>Transaction failed!.</b>,
      });
    } catch (error) { }
  }

  useEffect(() => {
    const fetchDepositedTokenInfo = () => {
      const tokenContract = new ethers.Contract(
        tokenAddress, 
        ERC20TokenArtifacts.abi,
        library
      )
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )
      tokenContract.balanceOf(account).then(setBalance).catch();
      tokenContract.decimals().then(setUnit).catch();
      chrl2._balances(account, tokenAddress).then(setDeposit).catch();
    };
    try {
      fetchDepositedTokenInfo();
    } catch (error) {
    }
  }, [library, tokenAddress, account]);

  const depositTokens = async () => {
    const signer = library.getSigner();
    try {
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )
      const value = ethers.BigNumber.from(amount).mul(ethers.BigNumber.from(10).pow(unit))
      const calldata = chrl2.interface.encodeFunctionData("deposit", [tokenAddress, value])
      const txPrams = {
        to: chrL2Address,
        value: '0x0',
        data: calldata
      };
      const transaction = await signer.sendTransaction(txPrams);
      toast.promise(transaction.wait(), {
        loading: `Transaction submitted. Wait for confirmation...`,
        success: <b>Transaction confirmed!</b>,
        error: <b>Transaction failed!.</b>,
      });
    } catch (error) {
    }
  };

  const approveTokens = async () => {
    const signer = library.getSigner();
    try {
      const tokenContract = new ethers.Contract(tokenAddress, ERC20TokenArtifacts.abi, library)
      const value = ethers.BigNumber.from(amount).mul(ethers.BigNumber.from(10).pow(unit))
      const calldata = tokenContract.interface.encodeFunctionData("approve", [chrL2Address, value]);
      const txPrams = {
        to: tokenAddress,
        value: '0x0',
        data: calldata,
      };
      const transaction = await signer.sendTransaction(txPrams);
      toast.promise(transaction.wait(), {
        loading: `Transaction submitted. Wait for confirmation...`,
        success: <b>Transaction confirmed!</b>,
        error: <b>Transaction failed!.</b>,
      });
    } catch (error) {
    }
  };  

  return (
    <div className="relative py-3 sm:max-w-5xl sm:mx-auto">
      {chainId !== 4 && (
        <>
          <div className="alert">
            <div className="flex-1">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                stroke="#ff5722"
                className="w-6 h-6 mx-2"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636"
                />
              </svg>
              <label>Please connect to the Rinkeby testnet for testing.</label>
            </div>
          </div>
          <div className="divider"></div>
        </>
      )}

      <div className="flex items-center w-full px-4 py-10 bg-cover card bg-base-200">
        <TokenInfo tokenAddress={tokenAddress} />

        <div className="text-center shadow-2xl card">
          <div className="card-body">
            <h2 className="card-title">ERC20 Token Deposit</h2>
            <div className="shadow stats">
              <div className="stat">
                <div className="stat-title">Balance</div>
                <div className="stat-value">{Number(formatUnits(balance, unit)).toFixed(6)}</div>
              </div>
              <div className="stat">
                <div className="stat-title">Deposited Ammount</div>
                <div className="stat-value">{Number(formatUnits(deposite, unit)).toFixed(6)}</div>
              </div>
              <div className="stat">
                <div className="stat-title">New Deposite</div>
                <div className="stat-value">{amount}</div>
              </div>
            </div>

            <input
              type="range"
              max="10"
              value={amount}
              onChange={(evt) => setAmount(evt.target.valueAsNumber)}
              className="range range-accent"
            />
            <div>
              <div className="justify-center card-actions">
                <button onClick={approveTokens} type="button" className="btn btn-outline btn-accent">
                  Approve
                </button>
                <button onClick={depositTokens} type="button" className="btn btn-outline btn-accent">
                  Deposit
                </button>
              </div>
            </div>
          </div>
        </div>
        <div className="divider"></div>


        <div className="text-center shadow-2xl card"><div className="card-body">
          <div class="form-control">
            <label class="label">
              <span class="label-text">Event Hash</span>
            </label>
            <input type="text" placeholder="Event Hash" class="input input-bordered" value={eventHash} size="80"
              onChange={(evt) => setEventHash(evt.target.value)}/>
          </div>
          <div className="justify-center card-actions">
            <button onClick={withdrawRequest} type="button" className="btn btn-outline btn-accent">
              Withdraw Request
            </button>
            <button onClick={withdraw} type="button" className="btn btn-outline btn-accent">
              Withdraw
            </button>
          </div>
        </div></div>
        <div className="divider"></div>
      </div>
    </div>
  );
};

export default ChrL2Contract;