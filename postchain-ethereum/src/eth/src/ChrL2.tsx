import { formatUnits } from "@ethersproject/units";
import { useWeb3React } from "@web3-react/core";
import { BigNumber, ethers } from "ethers";
import { toast } from "react-hot-toast";
import React, { useEffect, useState } from "react";
import { useQuery } from "react-query";

import ERC20TokenArtifacts from "./artifacts/contracts/token/ERC20.sol/ERC20.json";
import ChrL2Artifacts from "./artifacts/contracts/ChrL2.sol/ChrL2.json";

import { restClient, gtxClient, util } from "postchain-client"
import { hexZeroPad, keccak256 } from "ethers/lib/utils";
import { intToHex } from "ethjs-util";

const postchainURL = process.env.REACT_APP_POSTCHAIN_URL
const blockchainRID = process.env.REACT_APP_POSTCHAIN_BRID
const rest = restClient.createRestClient(postchainURL, blockchainRID, 5);
const client = gtxClient.createClient(
    rest,
    Buffer.from(blockchainRID, 'hex'),
    []
)

interface Props {
  chrL2Address: string;
  tokenAddress: string;
}

const TokenInfo = ({ tokenAddress, chrL2Address }: { tokenAddress: string, chrL2Address: string}) => {
  const { library, account } = useWeb3React();
  const fetchTokenInfo = async () => {
    const tokenContract = new ethers.Contract(tokenAddress, ERC20TokenArtifacts.abi, library);
    const name = await tokenContract.name();
    const symbol = await tokenContract.symbol();
    const decimals = await tokenContract.decimals();
    let balance = await client.query('__eth_balance_of', { "token": tokenAddress.toLowerCase(), "beneficiary": account.toLowerCase() })
    let withdraws = await client.query('get_withdrawal', {
      'token': tokenAddress.toLowerCase(),
      'beneficiary': account.toLowerCase()
    });
    withdraws = JSON.parse(JSON.stringify(withdraws))
    balance = balance.toString()
    return {
      name,
      symbol,
      decimals,
      balance,
      withdraws
    }
  }

  const { error, isLoading, data } = useQuery(["token-info", tokenAddress], fetchTokenInfo, {
    enabled: tokenAddress !== "",
  });

  if (error) return <div>failed to load</div>
  if (isLoading) return <div>loading...</div>

  var DecodeHexStringToByteArray = function (hexString: string) {
    var result = [];
    while (hexString.length >= 2) {
        result.push(parseInt(hexString.substring(0, 2), 16))
        hexString = hexString.substring(2, hexString.length)
    }
    return result;
  }

  var calculateEventLeafHash = function (serial: number, token: string, beneficiary: string, amount: number) {
    let s = hexZeroPad(intToHex(serial), 32)
    let t = hexZeroPad(token, 32)
    let b = hexZeroPad(beneficiary, 32)
    let a = hexZeroPad(intToHex(amount), 32)
    let event: string = ''
    event = event.concat(s.substring(2, s.length))
    event = event.concat(t.substring(2, t.length))
    event = event.concat(b.substring(2, b.length))
    event = event.concat(a.substring(2, a.length))
    let eventHash = keccak256(DecodeHexStringToByteArray(event))
    return eventHash.substring(2, eventHash.length)
  }

  const withdrawRequest = async (serial: number, token: string, beneficiary: string, amount: number) => {
    const signer = library.getSigner()
    const eventHash = calculateEventLeafHash(serial, token, beneficiary, amount)
    try {
      let data = await client.query('get_event_merkle_proof', { "eventHash": eventHash })
      let event = JSON.parse(JSON.stringify(data))
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )

      const blockHeader = "0x" + event.blockHeader
      const blockWitness = event.blockWitness
      let sigs = new Array<string>(blockWitness.length)
      for (let i = 0; i < blockWitness.length; i++) {
        sigs[i] = "0x" + blockWitness[i].sig
      }

      const eventData = event.eventData
      const evtPosition = eventData[1]
      const evtHash = "0x" + eventData[2]
      const evtData = "0x" + eventData[3]
      const merkleProofs = event.merkleProofs
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
      })
    } catch (error) { }
  }

  const withdraw = async (serial: number, token: string, beneficiary: string, amount: number) => {
    const signer = library.getSigner();
    const eventHash = calculateEventLeafHash(serial, token, beneficiary, amount)
    try {
      let data = await client.query('get_event_merkle_proof', { "eventHash": eventHash })
      let event = JSON.parse(JSON.stringify(data))
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )
      const eventData = event.eventData
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
      })
    } catch (error) { }
  }

  return (
    <div className="flex flex-col">
      <button className="btn">
        {data?.name}
        <div className="ml-2 badge">{data?.symbol}</div>
        <div className="ml-2 badge badge-info">{data?.decimals}</div>
      </button>
      <div className="shadow stats">
        <div className="stat">
          <div className="stat-title">Postchain Balance</div>
          <div className="stat-value">{Number(formatUnits(data?.balance ?? 0, data?.decimals)).toFixed(6)}</div>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="table w-full">
          <thead>
            <tr>
              <th>Serial</th>
              <th>Amount</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {data?.withdraws.map((w) => {
              return (<tr key={w?.serial}>
                <th>{w?.serial}</th>
                <td>{w?.amount}</td>
                <td>
                  <button type="button" className="btn btn-outline btn-accent" onClick={() => withdrawRequest(w?.serial, w?.token, w?.beneficiary, w?.amount)}>
                    Withdraw Request
                  </button>
                  <button type="button" className="btn btn-outline btn-accent" onClick={() => withdraw(w?.serial, w?.token, w?.beneficiary, w?.amount)}>
                    Withdraw
                  </button>
                </td>
              </tr>)
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

const ChrL2Contract = ({ chrL2Address, tokenAddress }: Props) => {
  const { library, chainId, account } = useWeb3React()
  const [balance, setBalance] = useState(BigNumber.from(0))
  const [deposite, setDeposit] = useState(BigNumber.from(0))
  const [amount, setAmount] = useState(0)
  const [withdrawAmount, setWithdrawAmount] = useState(0)
  const [unit, setUnit] = useState(18)

  const waitConfirmation = function(txRID) {
    return new Promise((resolve, reject) => {
      rest.status(txRID, (err, res) => {
        if (err) {
          resolve(err);
        } else {
          const status = res.status;
          switch (status) {
            case "confirmed":
              resolve(null)
              break;
            case "rejected":
              reject(Error("Message was rejected"))
              break
            case "unknown":
              reject(Error("Server lost our message"))
              break
            case "waiting":
              setTimeout(() => waitConfirmation(txRID).then(resolve, reject), 100)
              break
            default:
              console.log(status)
              reject(Error("got unexpected response from server"))
          }
        }
      })
    })
  }

  const postchainWithdraw = async () => {
    try {
      let sender = util.makeKeyPair()
      var tx = client.newTransaction([sender.pubKey])
      tx.addOperation("__withdraw", tokenAddress.toLowerCase(), account.toLowerCase(), withdrawAmount*1000000)
      tx.sign(sender.privKey, sender.pubKey)
      let txRID = tx.getTxRID()
      tx.send((err) => {
        if (err !== null) {
          console.log(err)
          return
        }
        waitConfirmation(txRID).catch(err => {
          console.log(err);
        })
      })
    } catch (error) {
      console.log(error)
    }
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
      tokenContract.balanceOf(account).then(setBalance).catch()
      tokenContract.decimals().then(setUnit).catch()
      chrl2._balances(tokenAddress).then(setDeposit).catch()
    };
    try {
      fetchDepositedTokenInfo();
    } catch (error) {
    }
  }, [library, tokenAddress, account]);

  const depositTokens = async () => {
    const signer = library.getSigner()
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
      const calldata = tokenContract.interface.encodeFunctionData("approve", [chrL2Address, value])
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
        <TokenInfo tokenAddress={tokenAddress} chrL2Address={chrL2Address}/>

        <div className="text-center shadow-2xl card">
          <div className="card-body">
            {/* <h2 className="card-title">ERC20 Token Deposit</h2> */}
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
          <div className="shadow stats">
            <div className="stat">
              <div className="stat-title">Amount</div>
              <div className="stat-value">{withdrawAmount}</div>
            </div>
          </div>
          <div className="form-control">
            <input type="range" max="10" className="range range-accent" value={withdrawAmount}
              onChange={(evt) => setWithdrawAmount(evt.target.valueAsNumber)}/>
          </div>
          <div className="justify-center card-actions">
            <button onClick={postchainWithdraw} type="button" className="btn btn-outline btn-accent">
              Withdraw on Postchain
            </button>
          </div>
        </div></div>
        <div className="divider"></div>
      </div>
    </div>
  );
};

export default ChrL2Contract;