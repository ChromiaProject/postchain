// SPDX-License-Identifier: GPL-3.0-only
pragma solidity ^0.8.0;

import "./Postchain.sol";

/**
 * @title ERC721 token receiver interface
 * @dev Interface for any contract that wants to support safeTransfers
 * from ERC721 asset contracts.
 */
interface IERC721Receiver {
    /**
     * @dev Whenever an {IERC721} `tokenId` token is transferred to this contract via {IERC721-safeTransferFrom}
     * by `operator` from `from`, this function is called.
     *
     * It must return its Solidity selector to confirm the token transfer.
     * If any other value is returned or the interface is not implemented by the recipient, the transfer will be reverted.
     *
     * The selector can be obtained in Solidity with `IERC721Receiver.onERC721Received.selector`.
     */
    function onERC721Received(
        address operator,
        address from,
        uint256 tokenId,
        bytes calldata data
    ) external returns (bytes4);
}

contract ChrL2 is IERC721Receiver {
    using Postchain for bytes32;
    using MerkleProof for bytes32[];

    mapping(ERC20 => uint256) public _balances;
    mapping (bytes32 => Withdraw) public _withdraw;
    address[] public directoryNodes;
    address[] public appNodes;

    // Each postchain event will be used to claim only one time.
    mapping (bytes32 => bool) private _events;

    struct Withdraw {
        ERC20 token;
        address beneficiary;
        uint256 amount;
        uint256 block_number;
        bool isWithdraw;
    }

    event Deposited(address indexed owner, ERC20 indexed token, uint256 value);
    event WithdrawRequest(address indexed beneficiary, ERC20 indexed token, uint256 value);
    event Withdrawal(address indexed beneficiary, ERC20 indexed token, uint256 value);

    constructor(address[] memory _directoryNodes, address[] memory _appNodes) {
        directoryNodes = _directoryNodes;
        appNodes = _appNodes;
    }

    /**
     * @dev See {IERC721Receiver-onERC721Received}.
     *
     * Always returns `IERC721Receiver.onERC721Received.selector`.
     */
    function onERC721Received(
        address,
        address,
        uint256,
        bytes memory
    ) public virtual override returns (bytes4) {
        return this.onERC721Received.selector;
    }

    function updateDirectoryNodes(bytes32 hash, bytes[] memory sigs, address[] memory _directoryNodes) public returns (bool) {
        if (!hash.isValidNodes(_directoryNodes)) revert("ChrL2: invalid directory node");
        if (!hash.isValidSignatures(sigs, directoryNodes)) revert("ChrL2: not enough require signature");
        for (uint i = 0; i < directoryNodes.length; i++) {
            directoryNodes.pop();
        }
        directoryNodes = _directoryNodes;
        return true;
    }

    function updateAppNodes(bytes32 hash, bytes[] memory sigs, address[] memory _appNodes) public returns (bool) {
        if (!hash.isValidNodes(_appNodes)) revert("ChrL2: invalid app node");
        if (!hash.isValidSignatures(sigs, directoryNodes)) revert("ChrL2: not enough require signature");
        for (uint i = 0; i < appNodes.length; i++) {
            appNodes.pop();
        }
        appNodes = _appNodes;
        return true;
    }

    function deposit(ERC20 token, uint256 amount) public returns (bool) {
        token.transferFrom(msg.sender, address(this), amount);
        _balances[token] += amount;
        emit Deposited(msg.sender, token, amount);
        return true;
    }

    function withdraw_request(
        bytes memory _event,
        Data.EventProof memory eventProof,
        bytes memory blockHeader,
        bytes[] memory sigs,
        Data.EL2ProofData memory el2Proof
    ) public {
        require(_events[eventProof.leaf] == false, "ChrL2: event hash was already used");
        {
            (bytes32 blockRid, bytes32 eventRoot, ) = Postchain.verifyBlockHeader(blockHeader, el2Proof);
            if (!Postchain.isValidSignatures(blockRid, sigs, appNodes)) revert("ChrL2: block signature is invalid");
            if (!MerkleProof.verify(eventProof.merkleProofs, eventProof.leaf, eventProof.position, eventRoot)) revert("ChrL2: invalid merkle proof");
        }

        _events[eventProof.leaf] = _updateWithdraw(eventProof.leaf, _event); // mark the event hash was already used.
    }

    function _updateWithdraw(bytes32 hash, bytes memory _event) internal returns (bool) {
        Withdraw storage wd = _withdraw[hash];
        {
            (ERC20 token, address beneficiary, uint256 amount) = hash.verifyEvent(_event);
            require(amount > 0 && amount <= _balances[token], "ChrL2: invalid amount to make request withdraw");
            wd.token = token;
            wd.beneficiary = beneficiary;
            wd.amount += amount;
            wd.block_number = block.number + 50;
            wd.isWithdraw = false;
            _withdraw[hash] = wd;
            emit WithdrawRequest(beneficiary, token, amount);
        }
        return true;
    }

    function withdraw(bytes32 _hash, address payable beneficiary) public {
        Withdraw storage wd = _withdraw[_hash];
        require(wd.beneficiary == beneficiary, "ChrL2: no fund for the beneficiary");
        require(wd.block_number <= block.number, "ChrL2: not mature enough to withdraw the fund");
        require(wd.isWithdraw == false, "ChrL2: fund was already claimed");
        require(wd.amount > 0 && wd.amount <= _balances[wd.token], "ChrL2: not enough amount to withdraw");
        wd.isWithdraw = true;
        uint value = wd.amount;
        wd.amount = 0;
        _balances[wd.token] -= value;
        wd.token.transfer(beneficiary, value);
        emit Withdrawal(beneficiary, wd.token, value);
    }
}
