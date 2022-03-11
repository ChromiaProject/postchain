// SPDX-License-Identifier: GPL-3.0-only
pragma solidity ^0.8.0;

// Upgradeable implementations
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/security/ReentrancyGuardUpgradeable.sol";

// Interfaces
import "@openzeppelin/contracts/interfaces/IERC721.sol";
import "@openzeppelin/contracts/interfaces/IERC721Receiver.sol";
import "@openzeppelin/contracts/interfaces/IERC20.sol";
import "@openzeppelin/contracts/interfaces/IERC721Metadata.sol";

// Internal libraries
import "./utils/Gtv.sol";
import "./Postchain.sol";

contract ChrL2 is Initializable, IERC721Receiver, ReentrancyGuardUpgradeable {
    // This contract is upgradeable. This imposes restrictions on how storage layout can be modified once it is deployed
    // Some instructions are also not allowed. Read more at: https://docs.openzeppelin.com/upgrades-plugins/1.x/writing-upgradeable
    using Postchain for bytes32;
    using MerkleProof for bytes32[];
    enum AssetType {
        ERC20,
        ERC721
    }

    mapping(IERC20 => uint256) public _balances;
    mapping(IERC721 => mapping(uint256 => address)) public _owners;
    mapping (bytes32 => Withdraw) public _withdraw;
    mapping (bytes32 => WithdrawNFT) public _withdrawNFT;
    address[] public directoryNodes;
    address[] public appNodes;

    // Each postchain event will be used to claim only one time.
    mapping (bytes32 => bool) private _events;

    enum Status {
        Pending,
        Withdrawable,
        Withdrawn
    }

    struct Withdraw {
        IERC20 token;
        address beneficiary;
        uint256 amount;
        uint256 block_number;
        Status status;
    }

    struct WithdrawNFT {
        IERC721 nft;
        address beneficiary;
        uint256 tokenId;
        uint256 block_number;
        Status status;
    }

    event Deposited(AssetType indexed asset, bytes payload);
    event WithdrawRequest(address indexed beneficiary, IERC20 indexed token, uint256 value);
    event WithdrawRequestNFT(address indexed beneficiary, IERC721 indexed token, uint256 tokenId);
    event Withdrawal(address indexed beneficiary, IERC20 indexed token, uint256 value);
    event WithdrawalNFT(address indexed beneficiary, IERC721 indexed nft, uint256 tokenId);

    function initialize(address[] memory _directoryNodes, address[] memory _appNodes) public initializer {
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
        string memory name = "";
        string memory symbol = "";
        uint8 decimals = 0;

        // We don't know if this token supports metadata functions or not so we have to query and handle failure
        bool success;
        bytes memory _name;
        bytes memory _symbol;
        bytes memory _decimals;
        (success, _name) = address(token).staticcall(abi.encodeWithSignature("name()"));
        if (success) {
            name = abi.decode(_name, (string));
        }
        (success, _symbol) = address(token).staticcall(abi.encodeWithSignature("symbol()"));
        if (success) {
            symbol = abi.decode(_symbol, (string));
        }
        (success, _decimals) = address(token).staticcall(abi.encodeWithSignature("decimals()"));
        if (success) {
            decimals = abi.decode(_decimals, (uint8));
        }

        // Encode arguments
        bytes memory args = abi.encodePacked(
            Gtv.encode(msg.sender),
            Gtv.encode(address(token)),
            Gtv.encode(amount),
            Gtv.encode(name),
            Gtv.encode(symbol),
            Gtv.encode(decimals)
        );
        bytes memory argArray = Gtv.encodeArray(args);

        // Do transfer
        token.transferFrom(msg.sender, address(this), amount);
        _balances[token] += amount;
        emit Deposited(AssetType.ERC20, argArray);
        return true;
    }

    function depositNFT(IERC721 nft, uint256 tokenId) public returns (bool) {
        nft.safeTransferFrom(msg.sender, address(this), tokenId);
        _owners[nft][tokenId] = msg.sender;
        string memory name = "";
        string memory symbol= "";
        string memory tokenURI = "";
        bytes memory args;
        if (nft.supportsInterface(type(IERC721Metadata).interfaceId)) {
            bool success;
            bytes memory _name;
            bytes memory _symbol;
            bytes memory _tokenURI;
            (success, _name) = address(nft).staticcall(abi.encodeWithSignature("name()"));
            require(success, "ChrL2: cannot get nft name");
            (success, _symbol) = address(nft).staticcall(abi.encodeWithSignature("symbol()"));
            require(success, "ChrL2: cannot get nft symbol");
            (success, _tokenURI) = address(nft).staticcall(abi.encodeWithSignature("tokenURI(uint256)", tokenId));
            require(success, "ChrL2: cannot get nft token URI");
            name = abi.decode(_name, (string));
            symbol = abi.decode(_symbol, (string));
            tokenURI = abi.decode(_tokenURI, (string));
        }

        // Encode arguments
        args = abi.encodePacked(
            Gtv.encode(msg.sender),
            Gtv.encode(address(nft)),
            Gtv.encode(tokenId),
            Gtv.encode(name),
            Gtv.encode(symbol),
            Gtv.encode(tokenURI)
        );
        emit Deposited(AssetType.ERC721, Gtv.encodeArray(args));
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
            wd.status = Status.Withdrawable;
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
            wd.status = Status.Withdrawable;
            _withdrawNFT[hash] = wd;
            emit WithdrawRequestNFT(beneficiary, nft, tokenId);
        }
        return true;
    }

    function withdraw(bytes32 _hash, address payable beneficiary) public nonReentrant {
        Withdraw storage wd = _withdraw[_hash];
        require(wd.beneficiary == beneficiary, "ChrL2: no fund for the beneficiary");
        require(wd.block_number <= block.number, "ChrL2: not mature enough to withdraw the fund");
        require(wd.status == Status.Withdrawable, "ChrL2: fund is pending or was already claimed");
        require(wd.amount > 0 && wd.amount <= _balances[wd.token], "ChrL2: not enough amount to withdraw");
        wd.status = Status.Withdrawn;
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
        require(wd.status == Status.Withdrawable, "ChrL2: nft is pending or was already claimed");
        require(_owners[wd.nft][tokenId] != address(0), "ChrL2: nft token id does not exist or was already claimed");
        wd.status = Status.Withdrawn;
        _owners[wd.nft][tokenId] = address(0);
        wd.nft.safeTransferFrom(address(this), beneficiary, tokenId);
        emit WithdrawalNFT(beneficiary, wd.nft, tokenId);
    }
}
