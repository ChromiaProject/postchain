// SPDX-License-Identifier: GPL-3.0-only
pragma solidity >=0.6.0 <=0.8.7;
pragma experimental ABIEncoderV2;

import "./Postchain.sol";
import "./token/ERC20.sol";

contract ChrL2 {
    using Postchain for bytes32;

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

    function withdraw_request(bytes calldata _event, bytes32 _hash,
        bytes calldata blockHeader,
        bytes[] calldata sigs,
        bytes32[] calldata merkleProofs,
        uint position
    ) public {
        require(_events[_hash] == false, "ChrL2: event hash was already used");
        _hash.verifyBlock(blockHeader, sigs, merkleProofs, position, appNodes);
        (ERC20 token, address beneficiary, uint256 amount) = _hash.verifyEvent(_event);
        require(amount > 0 && amount <= _balances[token], "ChrL2: invalid amount to make request withdraw");
        Withdraw storage wd = _withdraw[_hash];
        wd.token = token;
        wd.beneficiary = beneficiary;
        wd.amount += amount;
        wd.block_number = block.number + 50;
        wd.isWithdraw = false;
        _withdraw[_hash] = wd;
        _events[_hash] = true; // mark the event hash was already used.
        emit WithdrawRequest(beneficiary, token, amount);
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
