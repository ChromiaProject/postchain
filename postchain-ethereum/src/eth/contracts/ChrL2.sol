// SPDX-License-Identifier: GPL-3.0-only
pragma solidity ^0.8.0;

// Upgradeable implementations
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/security/ReentrancyGuardUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/access/OwnableUpgradeable.sol";

// Interfaces
import "@openzeppelin/contracts/interfaces/IERC721.sol";
import "@openzeppelin/contracts/interfaces/IERC721Metadata.sol";
import "@openzeppelin/contracts/interfaces/IERC721Receiver.sol";
import "@openzeppelin/contracts/interfaces/IERC20.sol";

// Internal libraries
import "./utils/Gtv.sol";
import "./Postchain.sol";

// This contract is upgradeable. This imposes restrictions on how storage layout can be modified once it is deployed
// Some instructions are also not allowed. Read more at: https://docs.openzeppelin.com/upgrades-plugins/1.x/writing-upgradeable
// Note: To enhance the security & decentralization, we should call transferOwnership() to external multi-sig owner after deploy the smart contract
contract ChrL2 is Initializable, OwnableUpgradeable, IERC721Receiver, ReentrancyGuardUpgradeable {

    uint8 constant ERC20_ACCOUNT_STATE_BYTE_SIZE = 64;
    uint8 constant ERC721_ACCOUNT_STATE_BYTE_SIZE = 64;
    using Postchain for bytes32;
    using MerkleProof for bytes32[];
    enum AssetType {
        ERC20,
        ERC721
    }

    struct PostchainBlock {
        uint height;
        bytes32 blockRid;
    }

    struct ERC20AccountState {
        IERC20 token;
        uint amount;
    }

    struct ERC721AccountState {
        IERC721 token;
        uint tokenId;
    }

    struct AccountStateNumber {
        uint blockHeight;
        uint accountNumber;
    }

    bool public isMassExit;
    PostchainBlock public massExitBlock;
    mapping(IERC20 => uint256) public _balances;
    mapping(IERC721 => mapping(uint256 => address)) public _owners;
    mapping (bytes32 => Withdraw) public _withdraw;
    mapping (bytes32 => WithdrawNFT) public _withdrawNFT;
    mapping(address => bool) validatorMap;
    address[] public validators;

    // Each postchain event will be used to claim only one time.
    mapping (bytes32 => bool) private _events;

    // Each account state snapshot will be used to claim only one time.
    mapping (bytes32 => bool) private _snapshots;

    enum Status {
        Pending,
        Withdrawable,
        Withdrawn
    }

    struct Transaction {
        address destination;
        uint value;
        bytes data;
        bool executed;
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

    event ValidatorAdded(address indexed _validator);
    event ValidatorRemoved(address indexed _validator);
    event Deposited(AssetType indexed asset, bytes payload);
    event WithdrawRequest(address indexed beneficiary, IERC20 indexed token, uint256 value);
    event WithdrawRequestNFT(address indexed beneficiary, IERC721 indexed token, uint256 tokenId);
    event Withdrawal(address indexed beneficiary, IERC20 indexed token, uint256 value);
    event WithdrawalNFT(address indexed beneficiary, IERC721 indexed nft, uint256 tokenId);
    event MassExit(uint indexed height, bytes32 indexed blockRid);
    event WithdrawalBySnapshot(address indexed beneficiary);

    modifier whenMassExit() {
        require(isMassExit, "ChrL2: mass exit was not triggered yet");
        _;
    }

    function initialize(address[] memory _validators) public initializer {
        __Ownable_init();
        validators = _validators;

        for (uint i = 0; i < validators.length; i++) {
            validatorMap[validators[i]] = true;
        }
    }
    
    function isValidator(address _addr) public view returns (bool) {
        return validatorMap[_addr];
    }
    
    function addValidator(address _validator) external onlyOwner {
        require(!validatorMap[_validator]);
        validators.push(_validator);
        validatorMap[_validator] = true;
        emit ValidatorAdded(_validator);
    }

    function removeValidator(address _validator) external onlyOwner {
        require(isValidator(_validator));
        uint index;
        uint validatorCount = validators.length;
        for (uint i = 0; i < validatorCount; i++) {
            if (validators[i] == _validator) {
                index = i;
                break;
            }
        }

        validatorMap[_validator] = false;
        validators[index] = validators[validatorCount - 1];
        validators.pop();

        emit ValidatorRemoved(_validator);
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

    function triggerMassExit(uint height, bytes32 blockRid) onlyOwner public {
        require(!isMassExit, "ChrL2: mass exit already set");
        isMassExit = true;
        massExitBlock = PostchainBlock(height, blockRid);
    }

    function postponeMassExit() onlyOwner whenMassExit public {
        isMassExit = false;
    }

    function updateMassExitBlock(uint height, bytes32 blockRid) onlyOwner whenMassExit public {
        massExitBlock = PostchainBlock(height, blockRid);
    }

    function pendingWithdraw(bytes32 _hash) onlyOwner public {
        Withdraw storage wd = _withdraw[_hash];
        require(wd.status == Status.Withdrawable, "ChrL2: withdraw request status is not withdrawable");
        wd.status = Status.Pending;
    }

    function unpendingWithdraw(bytes32 _hash) onlyOwner public {
        Withdraw storage wd = _withdraw[_hash];
        require(wd.status == Status.Pending, "ChrL2: withdraw request status is not pending");
        wd.status = Status.Withdrawable;
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
            if (!Postchain.isValidSignatures(blockRid, sigs, validators)) revert("ChrL2: block signature is invalid");
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

    /// @dev withdraw all account assets in the postchain snapshot when mass exit was triggered
    function withdrawBySnapshot(
        AccountStateNumber memory account,
        bytes calldata snapshot,
        bytes32[] memory stateProofs,
        bytes memory blockHeader,
        bytes[] memory sigs,
        Data.EL2ProofData memory el2Proof
    ) whenMassExit nonReentrant public  {
        bytes32 stateHash = keccak256(abi.encodePacked(snapshot));
        require(_snapshots[stateHash] == false, "ChrL2: snapshot already used");
        (bytes32 blockRid, , bytes32 stateRoot) = Postchain.verifyBlockHeader(blockHeader, el2Proof);
        require(blockRid == massExitBlock.blockRid, "ChrL2: account state block rid should equal to mass exit block rid");
        require(account.blockHeight <= massExitBlock.height, "ChrL2: account state number should less than or equal to mass exit block");
        if (!Postchain.isValidSignatures(blockRid, sigs, validators)) revert("ChrL2: block signature is invalid");
        if (!MerkleProof.verify(stateProofs, stateHash, account.accountNumber, stateRoot)) revert("ChrL2: invalid merkle proof");

        address beneficiary = abi.decode(snapshot[:32], (address));
        uint offset = 32;
        // Get byte size of all ERC20 balances
        uint byteSize = abi.decode(snapshot[offset:offset + 32], (uint));
        offset += 32;
        for (uint i = offset; i < offset + byteSize; i += ERC20_ACCOUNT_STATE_BYTE_SIZE) {
            ERC20AccountState memory accountState = abi.decode(snapshot[i:i + ERC20_ACCOUNT_STATE_BYTE_SIZE], (ERC20AccountState));
            if (accountState.amount > 0) {
                accountState.token.transfer(beneficiary, accountState.amount);
            }
        }
        offset += byteSize;

        // Get byte size of all ERC721 balances
        byteSize = abi.decode(snapshot[offset:offset + 32], (uint));
        offset += 32; // Byte size field (32)
        // Decode each balance to find the one we are looking for
        for (uint i = offset; i < offset + byteSize; i += ERC721_ACCOUNT_STATE_BYTE_SIZE) {
            ERC721AccountState memory accountState = abi.decode(snapshot[i:i + ERC721_ACCOUNT_STATE_BYTE_SIZE], (ERC721AccountState));
            accountState.token.safeTransferFrom(address(this), beneficiary, accountState.tokenId);
        }

        _snapshots[stateHash] = true;
        emit WithdrawalBySnapshot(beneficiary);
    }  
}
