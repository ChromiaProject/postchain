import { formatUnits } from "@ethersproject/units";
import { useWeb3React } from "@web3-react/core";
import { BigNumber, ethers } from "ethers";
import { toast } from "react-hot-toast";
import React, { useEffect, useState } from "react";
import { useQuery } from "react-query";

import ERC20TokenArtifacts from "./artifacts/@openzeppelin/contracts/token/ERC20/ERC20.sol/ERC20.json";
import ERC721TokenArtifacts from "./artifacts/@openzeppelin/contracts/token/ERC721/ERC721.sol/ERC721.json";
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

const sendTnx = async (signer, to, calldata) => {
  const txPrams = {
    to: to,
    value: '0x0',
    data: calldata
  };
  const transaction = await signer.sendTransaction(txPrams);
  toast.promise(transaction.wait(), {
    loading: `Transaction submitted. Wait for confirmation...`,
    success: <b>Transaction confirmed!</b>,
    error: <b>Transaction failed!.</b>,
  })
}

const TokenInfo = ({ tokenAddress, chrL2Address, tokenType, tokenId }: { tokenAddress: string, chrL2Address: string, tokenType: string, tokenId: number}) => {
  const { library, account } = useWeb3React();
  const fetchTokenInfo = async () => {
    var tokenContract;
    let balance;
    let withdraws;
    if (tokenType === "ERC721") {
      tokenContract = new ethers.Contract(tokenAddress, ERC721TokenArtifacts.abi, library);
      const hasToken = await client.query('eth_has_erc721', { "token_address": tokenAddress.toLowerCase(), "beneficiary": account.toLowerCase(), "token_id": tokenId })
      balance = hasToken ? 1 : 0;
      withdraws = await client.query('get_erc721_withdrawal', {
        'token_address': tokenAddress.toLowerCase(),
        'token_id': tokenId,
        'beneficiary': account.toLowerCase()
      });
    } else {
      tokenContract = new ethers.Contract(tokenAddress, ERC20TokenArtifacts.abi, library);
      balance = await client.query('eth_balance_of_erc20', { "token_address": tokenAddress.toLowerCase(), "beneficiary": account.toLowerCase() })
      withdraws = await client.query('get_erc20_withdrawal', {
        'token_address': tokenAddress.toLowerCase(),
        'beneficiary': account.toLowerCase()
      });
    }
    const name = await tokenContract.name();
    const symbol = await tokenContract.symbol();
    var decimals = 0;
    if (tokenType === "ERC20") {
      decimals = await tokenContract.decimals();
    }
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

  const calculateEventLeafHash = (...args: any) => {
    let event: string = ''
    args.forEach(arg => {
      if (typeof arg === 'number') {
        event += hexZeroPad(intToHex(arg), 32).substring(2)
      } else if (typeof arg === 'string') {
        event += hexZeroPad(arg, 32).substring(2)
      }
    })
    let eventHash = keccak256(DecodeHexStringToByteArray(event))
    return eventHash.substring(2, eventHash.length)
  }

  const withdrawRequest = async (eventHash: string) => {
    const signer = library.getSigner()
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

      const eventData = "0x" + event.eventData
      const eventProof = event.eventProof
      let merkleProofs = new Array<String>(eventProof.merkleProofs.length)
      for (let i = 0; i < eventProof.merkleProofs.length; i++) {
        merkleProofs[i] = "0x" + eventProof.merkleProofs[i]
      }
      const evtProof = {
        leaf: "0x" + eventProof.leaf,
        position: eventProof.position,
        merkleProofs: merkleProofs,
      }
      const el2MerkleProof = event.el2MerkleProof
      let extraMerkleProofs = new Array<String>(el2MerkleProof.extraMerkleProofs.length)
      for (let i = 0; i < el2MerkleProof.extraMerkleProofs.length; i++) {
        extraMerkleProofs[i] = "0x" + el2MerkleProof.extraMerkleProofs[i]
      }
      const el2Proof = {
        el2Leaf: "0x" + el2MerkleProof.el2Leaf,
        el2HashedLeaf: "0x" + el2MerkleProof.el2HashedLeaf,
        el2Position: el2MerkleProof.el2Position,
        extraRoot: "0x" + el2MerkleProof.extraRoot,
        extraMerkleProofs: extraMerkleProofs,
      }
      var calldata
      if (tokenType === "ERC20") {
        calldata = chrl2.interface.encodeFunctionData("withdrawRequest", [eventData, evtProof, blockHeader, sigs, el2Proof])
      } else {
        calldata = chrl2.interface.encodeFunctionData("withdrawRequestNFT", [eventData, evtProof, blockHeader, sigs, el2Proof])
      }
      await sendTnx(signer, chrL2Address, calldata)
    } catch (error) { }
  }

  const withdraw = async (eventHash: string) => {
    const signer = library.getSigner();
    const zeroPaddedEventHash = "0x" + eventHash
    try {
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )
      var calldata
      if (tokenType === "ERC20") {
        calldata = chrl2.interface.encodeFunctionData("withdraw", [zeroPaddedEventHash, account])
      } else {
        calldata = chrl2.interface.encodeFunctionData("withdrawNFT", [zeroPaddedEventHash, account])
      }
      await sendTnx(signer, chrL2Address, calldata)
    } catch (error) { }
  }

  const pending = async (serial: number, token: string, beneficiary: string, amount: number) => {
    const signer = library.getSigner();
    const eventHash = "0x" + calculateEventLeafHash(serial, token, beneficiary, amount)
    try {
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )
      console.log("eventHash: " + eventHash)
      let pendingWithdraw = chrl2.interface.encodeFunctionData("pendingWithdraw", [eventHash])
      console.log("pendingWithdraw: " + pendingWithdraw)
      let calldata = chrl2.interface.encodeFunctionData("submitTransaction", [chrL2Address, BigNumber.from(0), pendingWithdraw])
      await sendTnx(signer, chrL2Address, calldata)
    } catch (e) { 
      console.log(e.Message)
    }
  }

  const confirm =async () => {
    const signer = library.getSigner();
    try {
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )
      let calldata = chrl2.interface.encodeFunctionData("confirmTransaction", [BigNumber.from(0)])
      await sendTnx(signer, chrL2Address, calldata)
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
              <th>{tokenType === "ERC20" ? 'Amount' : 'Token ID'}</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {data?.withdraws?.map((w) => {
              const eventHash = tokenType === 'ERC20' ? calculateEventLeafHash(w.serial, w.token, w.beneficiary, w.amount)
                  : calculateEventLeafHash(w.serial, w.token, w.beneficiary, tokenId)
              return (<tr key={w?.serial}>
                <th>{w?.serial}</th>
                <td>{tokenType === "ERC20" ? Number(formatUnits(w?.amount.toString() ?? 0, data?.decimals)).toFixed(6) : tokenId}</td>
                <td>
                  <button type="button" className="btn btn-outline btn-accent" onClick={() => withdrawRequest(eventHash)}>
                    Withdraw Request
                  </button>
                  <button type="button" className="btn btn-outline btn-accent" onClick={() => withdraw(eventHash)}>
                    Withdraw
                  </button>
                  <button type="button" className="btn btn-outline btn-accent" onClick={() => pending(w?.serial, w?.token, w?.beneficiary, w?.value)}>
                    Pending
                  </button>
                  <button type="button" className="btn btn-outline btn-accent" onClick={() => confirm()}>
                    Confirm
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

const ChrL2Contract = ({ chrL2Address, tokenAddress}: Props) => {
  const { library, chainId, account } = useWeb3React()
  const [balance, setBalance] = useState(BigNumber.from(0))
  const [deposite, setDeposit] = useState(BigNumber.from(0))
  const [amount, setAmount] = useState(0)
  const [withdrawAmount, setWithdrawAmount] = useState(0)
  const [unit, setUnit] = useState(18)
  const tokenId = 65696
  var tokenType: string
  if (tokenAddress === "0x064e16771A4864561f767e4Ef4a6989fc4045aE7") {
    tokenType = "ERC721"
  } else {
    tokenType = "ERC20"
  }

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
      if (tokenType === "ERC721") {
        tx.addOperation("withdraw_ERC721", tokenAddress.toLowerCase(), account.toLowerCase(), tokenId)
      } else {
        const amount = ethers.BigNumber.from(withdrawAmount).mul(ethers.BigNumber.from(10).pow(unit)).toString()
        tx.addOperation("withdraw_ERC20", tokenAddress.toLowerCase(), account.toLowerCase(), parseInt(amount))
      }
      tx.sign(sender.privKey, sender.pubKey)
      let txRID = tx.getTxRID()
      tx.send((err) => {
        if (err !== null) {
          console.log(err)
          return
        }
        toast.promise(waitConfirmation(txRID), {
          loading: `Transaction submitted. Wait for confirmation...`,
          success: <b>Transaction confirmed!</b>,
          error: <b>Transaction failed!.</b>,
        })
      })
    } catch (error) {
      console.log(error)
    }
  }

  useEffect(() => {
    const fetchDepositedTokenInfo = () => {
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )

      var tokenContract;
      if (tokenType === "ERC721") {
        tokenContract = new ethers.Contract(
          tokenAddress,
          ERC721TokenArtifacts.abi,
          library
        )
        tokenContract.balanceOf(account).then(setBalance).catch()
        setUnit(0)
        chrl2._owners(tokenAddress, tokenId).then((owner: string) => {
          if (owner === account) {
            setDeposit(BigNumber.from(1))
          }
        }).catch()
      } else {
        tokenContract = new ethers.Contract(
          tokenAddress,
          ERC20TokenArtifacts.abi,
          library
        )
        tokenContract.balanceOf(account).then(setBalance).catch()
        tokenContract.decimals().then(setUnit).catch()
        chrl2._balances(tokenAddress).then(setDeposit).catch()
      }
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
      await sendTnx(signer, chrL2Address, calldata)
    } catch (error) {
    }
  };

  const depositNFTokens = async () => {
    const signer = library.getSigner()
    try {
      const chrl2 = new ethers.Contract(
        chrL2Address,
        ChrL2Artifacts.abi,
        library
      )
      const id = ethers.BigNumber.from(tokenId)
      const calldata = chrl2.interface.encodeFunctionData("depositNFT", [tokenAddress, id])
      await sendTnx(signer, chrL2Address, calldata)
    } catch (error) {
    }
  };

  const approveTokens = async () => {
    const signer = library.getSigner();
    try {
      const tokenContract = new ethers.Contract(tokenAddress, ERC20TokenArtifacts.abi, library)
      const value = ethers.BigNumber.from(amount).mul(ethers.BigNumber.from(10).pow(unit))
      const calldata = tokenContract.interface.encodeFunctionData("approve", [chrL2Address, value])
      await sendTnx(signer, tokenAddress, calldata)
    } catch (error) {
    }
  };

  const setApprovalForAll = async () => {
    const signer = library.getSigner();
    try {
      const tokenContract = new ethers.Contract(tokenAddress, ERC721TokenArtifacts.abi, library)
      const calldata = tokenContract.interface.encodeFunctionData("setApprovalForAll", [chrL2Address, true])
      await sendTnx(signer, tokenAddress, calldata)
    } catch (error) {
    }
  }

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
        <TokenInfo tokenAddress={tokenAddress} chrL2Address={chrL2Address} tokenType={tokenType} tokenId={tokenId}/>

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
            {tokenType === "ERC20" && (
              <>
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
              </>
            )}
            {tokenType === "ERC721" && (
              <>
                <div>
                  <div className="justify-center card-actions">
                    <button onClick={setApprovalForAll} type="button" className="btn btn-outline btn-accent">
                      Approve
                    </button>
                    <button onClick={depositNFTokens} type="button" className="btn btn-outline btn-accent">
                      Deposit
                    </button>
                  </div>
                </div>
              </>
            )}
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