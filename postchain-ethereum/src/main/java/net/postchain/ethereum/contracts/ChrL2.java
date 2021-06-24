package net.postchain.ethereum.contracts;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple3;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.8.4.
 */
@SuppressWarnings("rawtypes")
public class ChrL2 extends Contract {
    public static final String BINARY = "608060405234801561001057600080fd5b506113c9806100206000396000f3fe608060405234801561001057600080fd5b50600436106100a95760003560e01c80634d351b3c116100715780634d351b3c1461014c5780637e71ca5f1461015f57806380d2cfb714610172578063aa1969c614610185578063c0cffa5514610198578063c2bf17b0146101ab576100a9565b80630b8724ed146100ae5780631858cb5b146100d757806319045a25146100f95780633e7e92da1461011957806347e7ef2414610139575b600080fd5b6100c16100bc366004610d51565b6101be565b6040516100ce91906111fc565b60405180910390f35b6100ea6100e5366004610f78565b6102c0565b6040516100ce93929190611207565b61010c610107366004610e64565b61033b565b6040516100ce91906111c4565b61012c610127366004610d21565b610388565b6040516100ce919061107b565b6100c1610147366004610fc2565b6104c6565b61012c61015a366004610de9565b6105b8565b61012c61016d366004610d21565b61094b565b61012c610180366004610dc8565b6109c9565b61012c610193366004610f38565b610a40565b6100c16101a6366004610ca9565b610ac7565b61010c6101b9366004610ef7565b610b37565b60008382146101cf575060006102b7565b60005b848110156102b1578585828181106101e657fe5b90506020028101906101f89190611312565b905060411461020b5760009150506102b7565b83838281811061021757fe5b905060200201602081019061022c9190610c8d565b6001600160a01b03166102918888888581811061024557fe5b90506020028101906102579190611312565b8080601f01602080910402602001604051908101604052809392919081815260200183838082843760009201919091525061033b92505050565b6001600160a01b0316146102a95760009150506102b7565b6001016101d2565b50600190505b95945050505050565b60008060006102cd610be4565b6102d986880188610fed565b9050600087876040516102ed929190611092565b6040518091039020905085811461031f5760405162461bcd60e51b8152600401610316906112a9565b60405180910390fd5b5080516020820151604090920151909891975095509350505050565b6000815160411461035e5760405162461bcd60e51b815260040161031690611272565b60208201516040830151606084015160001a61037c86828585610b37565b93505050505b92915050565b60006001600883901c5b80156103a7576001919091019060081c610392565b60608260ff1667ffffffffffffffff811180156103c357600080fd5b506040519080825280601f01601f1916602001820160405280156103ee576020820181803683370190505b50859250905060015b8360ff168160ff16116104485760008360ff1690508060f81b8383870360ff168151811061042157fe5b60200101906001600160f81b031916908160001a9053505060089290921c916001016103f7565b506002600160a3856002016002878660405160200161046c96959493929190611170565b60408051601f1981840301815290829052610486916110a2565b602060405180830381855afa1580156104a3573d6000803e3d6000fd5b5050506040513d601f19601f820116820180604052508101906102b79190610d39565b6040516323b872dd60e01b81526000906001600160a01b038416906323b872dd906104f9903390309087906004016111d8565b602060405180830381600087803b15801561051357600080fd5b505af1158015610527573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061054b9190610d01565b50336000818152602081815260408083206001600160a01b03881680855292529182902080548601905590519091907f8752a472e571a816aea92eec8dae9baf628e840f4929fbcc2d155e6233ff68a7906105a790869061107b565b60405180910390a350600192915050565b600080600260006105c88c61094b565b6105d18c61094b565b6040516020016105e3939291906110ae565b60408051601f19818403018152908290526105fd916110a2565b602060405180830381855afa15801561061a573d6000803e3d6000fd5b5050506040513d601f19601f8201168201806040525081019061063d9190610d39565b90506000600260008a61064f8b610388565b604051602001610661939291906110ae565b60408051601f198184030181529082905261067b916110a2565b602060405180830381855afa158015610698573d6000803e3d6000fd5b5050506040513d601f19601f820116820180604052508101906106bb9190610d39565b90506000600260006106cc8a610388565b896040516020016106df939291906110ae565b60408051601f19818403018152908290526106f9916110a2565b602060405180830381855afa158015610716573d6000803e3d6000fd5b5050506040513d601f19601f820116820180604052508101906107399190610d39565b90506000600260087f43758c97091f5141260e8e3fd3a352a8fe106c353fcc7c9cdeec71ceeffdbb0f61076c8a8a610a40565b60405160200161077e939291906110ae565b60408051601f1981840301815290829052610798916110a2565b602060405180830381855afa1580156107b5573d6000803e3d6000fd5b5050506040513d601f19601f820116820180604052508101906107d89190610d39565b905060006002600086866040516020016107f4939291906110ae565b60408051601f198184030181529082905261080e916110a2565b602060405180830381855afa15801561082b573d6000803e3d6000fd5b5050506040513d601f19601f8201168201806040525081019061084e9190610d39565b9050600060026000858560405160200161086a939291906110ae565b60408051601f1981840301815290829052610884916110a2565b602060405180830381855afa1580156108a1573d6000803e3d6000fd5b5050506040513d601f19601f820116820180604052508101906108c49190610d39565b90506002600783836040516020016108de939291906110ae565b60408051601f19818403018152908290526108f8916110a2565b602060405180830381855afa158015610915573d6000803e3d6000fd5b5050506040513d601f19601f820116820180604052508101906109389190610d39565b9f9e505050505050505050505050505050565b60006002600160a16022600460208760405160200161096f969594939291906110d3565b60408051601f1981840301815290829052610989916110a2565b602060405180830381855afa1580156109a6573d6000803e3d6000fd5b5050506040513d601f19601f820116820180604052508101906103829190610d39565b6000821580156109d7575081155b156109e457506000610382565b82610a1757816040516020016109fa919061107b565b604051602081830303815290604052805190602001209050610382565b81610a2d57826040516020016109fa919061107b565b82826040516020016109fa929190611084565b60006002600160a16042600460408888604051602001610a66979695949392919061111c565b60408051601f1981840301815290829052610a80916110a2565b602060405180830381855afa158015610a9d573d6000803e3d6000fd5b5050506040513d601f19601f82011682018060405250810190610ac09190610d39565b9392505050565b600083815b86811015610b2a576001811b851680610b0257610afb838a8a85818110610aef57fe5b905060200201356109c9565b9250610b21565b610b1e898984818110610b1157fe5b90506020020135846109c9565b92505b50600101610acc565b5090911495945050505050565b60008360ff16601b1480610b4e57508360ff16601c145b610b6a5760405162461bcd60e51b8152600401610316906112d0565b600060018686868660405160008152602001604052604051610b8f949392919061121d565b6020604051602081039080840390855afa158015610bb1573d6000803e3d6000fd5b5050604051601f1901519150506001600160a01b0381166102b75760405162461bcd60e51b81526004016103169061123b565b604080516060810182526000808252602082018190529181019190915290565b60008083601f840112610c15578182fd5b50813567ffffffffffffffff811115610c2c578182fd5b6020830191508360208083028501011115610c4657600080fd5b9250929050565b60008083601f840112610c5e578182fd5b50813567ffffffffffffffff811115610c75578182fd5b602083019150836020828501011115610c4657600080fd5b600060208284031215610c9e578081fd5b8135610ac08161137b565b600080600080600060808688031215610cc0578081fd5b853567ffffffffffffffff811115610cd6578182fd5b610ce288828901610c04565b9099909850602088013597604081013597506060013595509350505050565b600060208284031215610d12578081fd5b81518015158114610ac0578182fd5b600060208284031215610d32578081fd5b5035919050565b600060208284031215610d4a578081fd5b5051919050565b600080600080600060608688031215610d68578081fd5b85359450602086013567ffffffffffffffff80821115610d86578283fd5b610d9289838a01610c04565b90965094506040880135915080821115610daa578283fd5b50610db788828901610c04565b969995985093965092949392505050565b60008060408385031215610dda578182fd5b50508035926020909101359150565b60008060008060008060008060e0898b031215610e04578283fd5b883597506020890135965060408901359550606089013594506080890135935060a0890135925060c089013567ffffffffffffffff811115610e44578283fd5b610e508b828c01610c4d565b999c989b5096995094979396929594505050565b60008060408385031215610e76578182fd5b8235915060208084013567ffffffffffffffff80821115610e95578384fd5b818601915086601f830112610ea8578384fd5b813581811115610eb457fe5b610ec6601f8201601f19168501611357565b91508082528784828501011115610edb578485fd5b8084840185840137810190920192909252919491935090915050565b60008060008060808587031215610f0c578182fd5b84359350602085013560ff81168114610f23578283fd5b93969395505050506040820135916060013590565b60008060208385031215610f4a578182fd5b823567ffffffffffffffff811115610f60578283fd5b610f6c85828601610c4d565b90969095509350505050565b600080600060408486031215610f8c578081fd5b833567ffffffffffffffff811115610fa2578182fd5b610fae86828701610c4d565b909790965060209590950135949350505050565b60008060408385031215610fd4578182fd5b8235610fdf8161137b565b946020939093013593505050565b600060608284031215610ffe578081fd5b6040516060810181811067ffffffffffffffff8211171561101b57fe5b80604052508235815260208301356020820152604083013560408201528091505092915050565b60008151815b818110156110625760208185018101518683015201611048565b818111156110705782828601525b509290920192915050565b90815260200190565b918252602082015260400190565b6000828483379101908152919050565b6000610ac08284611042565b60f89390931b6001600160f81b03191683526001830191909152602182015260410190565b6001600160f81b031960f897881b8116825295871b8616600182015293861b8516600285015291851b8416600384015290931b9091166004820152600581019190915260250190565b6001600160f81b031960f889811b8216835288811b8216600184015287811b8216600284015286811b8216600384015285901b16600482015260008284600584013791016005019081529695505050505050565b6001600160f81b031960f888811b8216835287811b8216600184015286811b8216600284015285811b8216600384015284901b16600482015260006111b86005830184611042565b98975050505050505050565b6001600160a01b0391909116815260200190565b6001600160a01b039384168152919092166020820152604081019190915260600190565b901515815260200190565b9283526020830191909152604082015260600190565b93845260ff9290921660208401526040830152606082015260800190565b60208082526018908201527f45434453413a20696e76616c6964207369676e61747572650000000000000000604082015260600190565b6020808252601f908201527f45434453413a20696e76616c6964207369676e6174757265206c656e67746800604082015260600190565b6020808252600d908201526c1a5b9d985b1a5908195d995b9d609a1b604082015260600190565b60208082526022908201527f45434453413a20696e76616c6964207369676e6174757265202776272076616c604082015261756560f01b606082015260800190565b6000808335601e19843603018112611328578283fd5b83018035915067ffffffffffffffff821115611342578283fd5b602001915036819003821315610c4657600080fd5b60405181810167ffffffffffffffff8111828210171561137357fe5b604052919050565b6001600160a01b038116811461139057600080fd5b5056fea26469706673582212209a76b1c34c4aa0175fd25593063fcf8d9a82243fac5cb4f4040659ca4478a75664736f6c63430007050033";

    public static final String FUNC_DEPOSIT = "deposit";

    public static final String FUNC_HASHBLOCKHEADER = "hashBlockHeader";

    public static final String FUNC_HASHGTVBYTES32LEAF = "hashGtvBytes32Leaf";

    public static final String FUNC_HASHGTVBYTES64LEAF = "hashGtvBytes64Leaf";

    public static final String FUNC_HASHGTVINTEGERLEAF = "hashGtvIntegerLeaf";

    public static final String FUNC_recover = "recover";

    public static final String FUNC_SHA3HASH = "sha3Hash";

    public static final String FUNC_VERIFYBLOCKSIG = "verifyBlockSig";

    public static final String FUNC_VERIFYMERKLEPROOF = "verifyMerkleProof";

    public static final String FUNC_VERIFYPROOF = "verifyProof";

    public static final Event DEPOSITED_EVENT = new Event("Deposited", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    @Deprecated
    protected ChrL2(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected ChrL2(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected ChrL2(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected ChrL2(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<DepositedEventResponse> getDepositedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(DEPOSITED_EVENT, transactionReceipt);
        ArrayList<DepositedEventResponse> responses = new ArrayList<DepositedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DepositedEventResponse typedResponse = new DepositedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.token = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<DepositedEventResponse> depositedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, DepositedEventResponse>() {
            @Override
            public DepositedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(DEPOSITED_EVENT, log);
                DepositedEventResponse typedResponse = new DepositedEventResponse();
                typedResponse.log = log;
                typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.token = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<DepositedEventResponse> depositedEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEPOSITED_EVENT));
        return depositedEventFlowable(filter);
    }

    public RemoteFunctionCall<TransactionReceipt> deposit(String token, BigInteger amount) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_DEPOSIT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, token), 
                new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<byte[]> hashBlockHeader(byte[] blockchainRid, byte[] previousBlockRid, byte[] merkleRootHashHashedLeaf, BigInteger timestamp, BigInteger height, byte[] dependeciesHashedLeaf, byte[] l2RootHash) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_HASHBLOCKHEADER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(blockchainRid), 
                new org.web3j.abi.datatypes.generated.Bytes32(previousBlockRid), 
                new org.web3j.abi.datatypes.generated.Bytes32(merkleRootHashHashedLeaf), 
                new org.web3j.abi.datatypes.generated.Uint256(timestamp), 
                new org.web3j.abi.datatypes.generated.Uint256(height), 
                new org.web3j.abi.datatypes.generated.Bytes32(dependeciesHashedLeaf), 
                new org.web3j.abi.datatypes.DynamicBytes(l2RootHash)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<byte[]> hashGtvBytes32Leaf(byte[] value) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_HASHGTVBYTES32LEAF, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(value)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<byte[]> hashGtvBytes64Leaf(byte[] value) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_HASHGTVBYTES64LEAF, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicBytes(value)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<byte[]> hashGtvIntegerLeaf(BigInteger value) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_HASHGTVINTEGERLEAF, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(value)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<String> recover(byte[] hash, byte[] signature) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_recover, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(hash), 
                new org.web3j.abi.datatypes.DynamicBytes(signature)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> recover(byte[] hash, BigInteger v, byte[] r, byte[] s) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_recover, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(hash), 
                new org.web3j.abi.datatypes.generated.Uint8(v), 
                new org.web3j.abi.datatypes.generated.Bytes32(r), 
                new org.web3j.abi.datatypes.generated.Bytes32(s)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<byte[]> sha3Hash(byte[] left, byte[] right) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_SHA3HASH, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(left), 
                new org.web3j.abi.datatypes.generated.Bytes32(right)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<Boolean> verifyBlockSig(byte[] message, List<byte[]> sigs, List<String> signers) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_VERIFYBLOCKSIG, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(message), 
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.DynamicBytes>(
                        org.web3j.abi.datatypes.DynamicBytes.class,
                        org.web3j.abi.Utils.typeMap(sigs, org.web3j.abi.datatypes.DynamicBytes.class)), 
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                        org.web3j.abi.datatypes.Address.class,
                        org.web3j.abi.Utils.typeMap(signers, org.web3j.abi.datatypes.Address.class))), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<Boolean> verifyMerkleProof(List<byte[]> proofs, byte[] leaf, BigInteger position, byte[] root) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_VERIFYMERKLEPROOF, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes32>(
                        org.web3j.abi.datatypes.generated.Bytes32.class,
                        org.web3j.abi.Utils.typeMap(proofs, org.web3j.abi.datatypes.generated.Bytes32.class)), 
                new org.web3j.abi.datatypes.generated.Bytes32(leaf), 
                new org.web3j.abi.datatypes.generated.Uint256(position), 
                new org.web3j.abi.datatypes.generated.Bytes32(root)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<Tuple3<byte[], BigInteger, byte[]>> verifyProof(byte[] value, byte[] proof) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_VERIFYPROOF, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicBytes(value), 
                new org.web3j.abi.datatypes.generated.Bytes32(proof)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Uint256>() {}, new TypeReference<Bytes32>() {}));
        return new RemoteFunctionCall<Tuple3<byte[], BigInteger, byte[]>>(function,
                new Callable<Tuple3<byte[], BigInteger, byte[]>>() {
                    @Override
                    public Tuple3<byte[], BigInteger, byte[]> call() throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple3<byte[], BigInteger, byte[]>(
                                (byte[]) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue(), 
                                (byte[]) results.get(2).getValue());
                    }
                });
    }

    @Deprecated
    public static ChrL2 load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new ChrL2(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static ChrL2 load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new ChrL2(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static ChrL2 load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new ChrL2(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static ChrL2 load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new ChrL2(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<ChrL2> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(ChrL2.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<ChrL2> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(ChrL2.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    public static RemoteCall<ChrL2> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(ChrL2.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<ChrL2> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(ChrL2.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class DepositedEventResponse extends BaseEventResponse {
        public String owner;

        public String token;

        public BigInteger value;
    }
}
