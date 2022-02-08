// SPDX-License-Identifier: GPL-3.0-only
pragma solidity ^0.8.0;

import "./Postchain.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721Receiver.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Metadata.sol";

contract ChrL2 is IERC721Receiver, ReentrancyGuard {
    using Postchain for bytes32;
    using MerkleProof for bytes32[];

    mapping(IERC20 => uint256) public _balances;
    mapping(IERC721 => mapping(uint256 => address)) public _owners;
    mapping (bytes32 => Withdraw) public _withdraw;
    mapping (bytes32 => WithdrawNFT) public _withdrawNFT;
    address[] public directoryNodes;
    address[] public appNodes;

    // Each postchain event will be used to claim only one time.
    mapping (bytes32 => bool) private _events;

    struct Withdraw {
        IERC20 token;
        address beneficiary;
        uint256 amount;
        uint256 block_number;
        bool isWithdraw;
    }

    struct WithdrawNFT {
        IERC721 nft;
        address beneficiary;
        uint256 tokenId;
        uint256 block_number;
        bool isWithdraw;
    }

    event Deposited(address indexed owner, IERC20 indexed token, uint256 value);
    event DepositedNFT(address indexed owner, IERC721 indexed nft, uint256 tokenId, bytes name, bytes symbol, bytes tokenURI);
    event WithdrawRequest(address indexed beneficiary, IERC20 indexed token, uint256 value);
    event WithdrawRequestNFT(address indexed beneficiary, IERC721 indexed token, uint256 tokenId);
    event Withdrawal(address indexed beneficiary, IERC20 indexed token, uint256 value);
    event WithdrawalNFT(address indexed beneficiary, IERC721 indexed nft, uint256 tokenId);

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

    function deposit(IERC20 token, uint256 amount) public returns (bool) {
        token.transferFrom(msg.sender, address(this), amount);
        _balances[token] += amount;
        emit Deposited(msg.sender, token, amount);
        return true;
    }

    function depositNFT(IERC721 nft, uint256 tokenId) public returns (bool) {
        nft.safeTransferFrom(msg.sender, address(this), tokenId);
        _owners[nft][tokenId] = msg.sender;
        if (nft.supportsInterface(type(IERC721Metadata).interfaceId)) {
            bool success;
            bytes memory name;
            bytes memory symbol;
            bytes memory tokenURI;
            (success, name) = address(nft).staticcall(abi.encodeWithSignature("name()"));
            require(success, "ChrL2: cannot get nft name");
            (success, symbol) = address(nft).staticcall(abi.encodeWithSignature("symbol()"));
            require(success, "ChrL2: cannot get nft symbol");
            (success, tokenURI) = address(nft).staticcall(abi.encodeWithSignature("tokenURI(uint256)", tokenId));
            require(success, "ChrL2: cannot get nft token URI");
            emit DepositedNFT(msg.sender, nft, tokenId,
                abi.decode(name, (bytes)), abi.decode(symbol, (bytes)), abi.decode(tokenURI, (bytes)));
        }
        emit DepositedNFT(msg.sender, nft, tokenId, "", "", "");
        return true;
    }

    function withdrawRequest(
        bytes memory _event,
        Data.EventProof memory eventProof,
        bytes memory blockHeader,
        bytes[] memory sigs,
        Data.EL2ProofData memory el2Proof
    ) public nonReentrant {
        _withdrawRequest(eventProof, blockHeader, sigs, el2Proof);
        _events[eventProof.leaf] = _updateWithdraw(eventProof.leaf, _event); // mark the event hash was already used.
    }

    function withdrawRequestNFT(
        bytes memory _event,
        Data.EventProof memory eventProof,
        bytes memory blockHeader,
        bytes[] memory sigs,
        Data.EL2ProofData memory el2Proof
    ) public nonReentrant {

        _withdrawRequest(eventProof, blockHeader, sigs, el2Proof);
        _events[eventProof.leaf] = _updateWithdrawNFT(eventProof.leaf, _event); // mark the event hash was already used.
    }

    function _withdrawRequest(
        Data.EventProof memory eventProof,
        bytes memory blockHeader,
        bytes[] memory sigs,
        Data.EL2ProofData memory el2Proof        
    ) internal view {
        require(_events[eventProof.leaf] == false, "ChrL2: event hash was already used");
        {
            (bytes32 blockRid, bytes32 eventRoot, ) = Postchain.verifyBlockHeader(blockHeader, el2Proof);
            if (!Postchain.isValidSignatures(blockRid, sigs, appNodes)) revert("ChrL2: block signature is invalid");
            if (!MerkleProof.verify(eventProof.merkleProofs, eventProof.leaf, eventProof.position, eventRoot)) revert("ChrL2: invalid merkle proof");
        }
        return;
    }

    function _updateWithdraw(bytes32 hash, bytes memory _event) internal returns (bool) {
        Withdraw storage wd = _withdraw[hash];
        {
            (IERC20 token, address beneficiary, uint256 amount) = hash.verifyEvent(_event);
            require(amount > 0 && amount <= _balances[token], "ChrL2: invalid amount to make request withdraw");
            wd.token = token;
            wd.beneficiary = beneficiary;
            wd.amount = amount;
            wd.block_number = block.number + 50;
            wd.isWithdraw = false;
            _withdraw[hash] = wd;
            emit WithdrawRequest(beneficiary, token, amount);
        }
        return true;
    }

    function _updateWithdrawNFT(bytes32 hash, bytes memory _event) internal returns (bool) {
        WithdrawNFT storage wd = _withdrawNFT[hash];
        {
            (IERC721 nft, address beneficiary, uint256 tokenId) = hash.verifyEventNFT(_event);
            require(_owners[nft][tokenId] != address(0), "ChrL2: invalid token id to make request withdraw");
            wd.nft = nft;
            wd.beneficiary = beneficiary;
            wd.tokenId = tokenId;
            wd.block_number = block.number + 50;
            wd.isWithdraw = false;
            _withdrawNFT[hash] = wd;
            emit WithdrawRequestNFT(beneficiary, nft, tokenId);
        }
        return true;
    } 

    function withdraw(bytes32 _hash, address payable beneficiary) public nonReentrant {
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

    function withdrawNFT(bytes32 _hash, address payable beneficiary) public nonReentrant {
        WithdrawNFT storage wd = _withdrawNFT[_hash];
        uint tokenId = wd.tokenId;
        require(wd.beneficiary == beneficiary, "ChrL2: no nft for the beneficiary");
        require(wd.block_number <= block.number, "ChrL2: not mature enough to withdraw the nft");
        require(wd.isWithdraw == false, "ChrL2: nft was already claimed");
        require(_owners[wd.nft][tokenId] != address(0), "ChrL2: nft token id does not exist or was already claimed");
        wd.isWithdraw = true;
        _owners[wd.nft][tokenId] = address(0);
        wd.nft.safeTransferFrom(address(this), beneficiary, tokenId);
        emit WithdrawalNFT(beneficiary, wd.nft, tokenId);
    } 
}
