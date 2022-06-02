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
import "./Postchain.sol";

// This contract is upgradeable. This imposes restrictions on how storage layout can be modified once it is deployed
// Some instructions are also not allowed. Read more at: https://docs.openzeppelin.com/upgrades-plugins/1.x/writing-upgradeable
// Note: To enhance the security & decentralization, we should call transferOwnership() to external multi-sig owner after deploy the smart contract
contract TokenBridge is Initializable, OwnableUpgradeable, IERC721Receiver, ReentrancyGuardUpgradeable {

    uint8 constant ERC20_ACCOUNT_STATE_BYTE_SIZE = 64;
    uint8 constant ERC721_ACCOUNT_STATE_BYTE_SIZE = 64;
    using EC for bytes32;
    using Postchain for bytes32;
    using MerkleProof for bytes32[];

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

    mapping (IERC20 => uint256) public _balances;
    mapping (IERC721 => mapping(uint256 => address)) public _owners;
    mapping (bytes32 => Withdraw) public _withdraw;
    mapping (bytes32 => WithdrawNFT) public _withdrawNFT;
    mapping (uint => mapping(address => bool)) validatorMap;
    mapping (uint => address[]) public validators; // postchain block height => validators
    uint[] public validatorHeights;

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

    event ValidatorAdded(uint height, address indexed _validator);
    event ValidatorRemoved(uint height, address indexed _validator);
    event DepositedERC20(address indexed sender, IERC20 indexed token, uint amount, string name, string symbol, uint8 decimals);
    event DepositedERC721(address indexed sender, IERC721 indexed nft, uint tokenId, string name, string symbol, string tokenURI);
    event WithdrawRequest(address indexed beneficiary, IERC20 indexed token, uint256 value);
    event WithdrawRequestNFT(address indexed beneficiary, IERC721 indexed token, uint256 tokenId);
    event Withdrawal(address indexed beneficiary, IERC20 indexed token, uint256 value);
    event WithdrawalNFT(address indexed beneficiary, IERC721 indexed nft, uint256 tokenId);

    function initialize(address[] memory _validators) public initializer {
        __Ownable_init();
        validators[0] = _validators;

        for (uint i = 0; i < validators[0].length; i++) {
            validatorMap[0][validators[0][i]] = true;
        }
        validatorHeights.push(0);
    }
    
    function isValidator(uint _height, address _addr) public view returns (bool) {
        return validatorMap[_height][_addr];
    }
    
    function addValidator(uint _height, address _validator) external onlyOwner {
        if (_height < validatorHeights[validatorHeights.length-1]) {
            revert("TokenBridge: cannot update previous heights' validator");
        } else if (_height > validatorHeights[validatorHeights.length-1]) {
            validatorHeights.push(_height);
        }
        require(!validatorMap[_height][_validator]);
        validators[_height].push(_validator);
        validatorMap[_height][_validator] = true;
        emit ValidatorAdded(_height, _validator);
    }

    function removeValidator(uint _height, address _validator) external onlyOwner {
        if (_height < validatorHeights[validatorHeights.length-1]) {
            revert("TokenBridge: cannot update previous heights' validator");
        }
        require(isValidator(_height, _validator));
        uint index;
        uint validatorCount = validators[_height].length;
        for (uint i = 0; i < validatorCount; i++) {
            if (validators[_height][i] == _validator) {
                index = i;
                break;
            }
        }

        validatorMap[_height][_validator] = false;
        validators[_height][index] = validators[_height][validatorCount - 1];
        validators[_height].pop();

        emit ValidatorRemoved(_height, _validator);
    }

    function getValidatorHeight(uint _height) external view returns (uint) {
        return _getValidatorHeight(_height);
    }

    function _getValidatorHeight(uint _height) internal view returns (uint) {
        uint lastIndex = validatorHeights.length-1;
        uint lastHeight = validatorHeights[lastIndex];
        if (_height >= lastHeight) {
            return lastHeight;
        } else {
            for (uint i = lastIndex; i > 0; i--) {
                if (_height < validatorHeights[i] && _height >= validatorHeights[i-1]) {
                    return validatorHeights[i-1];
                }
            }
            return 0;
        }
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

    function pendingWithdraw(bytes32 _hash) onlyOwner public {
        Withdraw storage wd = _withdraw[_hash];
        require(wd.status == Status.Withdrawable, "TokenBridge: withdraw request status is not withdrawable");
        wd.status = Status.Pending;
    }

    function unpendingWithdraw(bytes32 _hash) onlyOwner public {
        Withdraw storage wd = _withdraw[_hash];
        require(wd.status == Status.Pending, "TokenBridge: withdraw request status is not pending");
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

        // Do transfer
        token.transferFrom(msg.sender, address(this), amount);
        _balances[token] += amount;
        emit DepositedERC20(msg.sender, token, amount, name, symbol, decimals);
        return true;
    }

    function depositNFT(IERC721 nft, uint256 tokenId) public returns (bool) {
        nft.safeTransferFrom(msg.sender, address(this), tokenId);
        _owners[nft][tokenId] = msg.sender;
        string memory name = "";
        string memory symbol= "";
        string memory tokenURI = "";
        if (nft.supportsInterface(type(IERC721Metadata).interfaceId)) {
            bool success;
            bytes memory _name;
            bytes memory _symbol;
            bytes memory _tokenURI;
            (success, _name) = address(nft).staticcall(abi.encodeWithSignature("name()"));
            require(success, "TokenBridge: cannot get nft name");
            (success, _symbol) = address(nft).staticcall(abi.encodeWithSignature("symbol()"));
            require(success, "TokenBridge: cannot get nft symbol");
            (success, _tokenURI) = address(nft).staticcall(abi.encodeWithSignature("tokenURI(uint256)", tokenId));
            require(success, "TokenBridge: cannot get nft token URI");
            name = abi.decode(_name, (string));
            symbol = abi.decode(_symbol, (string));
            tokenURI = abi.decode(_tokenURI, (string));
        }

        emit DepositedERC721(msg.sender, nft, tokenId, name, symbol, tokenURI);
        return true;
    }

    /**
     * @dev signers should be order ascending
     */
    function withdrawRequest(
        bytes memory _event,
        Data.EventProof memory eventProof,
        bytes memory blockHeader,
        bytes[] memory sigs,
        address[] memory signers,
        Data.ExtraProofData memory extraProof
    ) external nonReentrant {
        _withdrawRequest(eventProof, blockHeader, sigs, signers, extraProof);
        _events[eventProof.leaf] = _updateWithdraw(eventProof.leaf, _event); // mark the event hash was already used.
    }

    /**
     * @dev signers should be order ascending
     */
    function withdrawRequestNFT(
        bytes memory _event,
        Data.EventProof memory eventProof,
        bytes memory blockHeader,
        bytes[] memory sigs,
        address[] memory signers,
        Data.ExtraProofData memory extraProof
    ) external nonReentrant {

        _withdrawRequest(eventProof, blockHeader, sigs, signers, extraProof);
        _events[eventProof.leaf] = _updateWithdrawNFT(eventProof.leaf, _event); // mark the event hash was already used.
    }

    function _withdrawRequest(
        Data.EventProof memory eventProof,
        bytes memory blockHeader,
        bytes[] memory sigs,
        address[] memory signers,
        Data.ExtraProofData memory extraProof
    ) internal view {
        require(_events[eventProof.leaf] == false, "TokenBridge: event hash was already used");
        {
            (uint height, bytes32 blockRid, bytes32 eventRoot, ) = Postchain.verifyBlockHeader(blockHeader, extraProof);
            if (!isValidSignatures(_getValidatorHeight(height), blockRid, sigs, signers)) revert("TokenBridge: block signature is invalid");
            if (!MerkleProof.verify(eventProof.merkleProofs, eventProof.leaf, eventProof.position, eventRoot)) revert("TokenBridge: invalid merkle proof");
        }
        return;
    }

    function _updateWithdraw(bytes32 hash, bytes memory _event) internal returns (bool) {
        Withdraw storage wd = _withdraw[hash];
        {
            (IERC20 token, address beneficiary, uint256 amount) = hash.verifyEvent(_event);
            require(amount > 0 && amount <= _balances[token], "TokenBridge: invalid amount to make request withdraw");
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
            require(_owners[nft][tokenId] != address(0), "TokenBridge: invalid token id to make request withdraw");
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
        require(wd.beneficiary == beneficiary, "TokenBridge: no fund for the beneficiary");
        require(wd.block_number <= block.number, "TokenBridge: not mature enough to withdraw the fund");
        require(wd.status == Status.Withdrawable, "TokenBridge: fund is pending or was already claimed");
        require(wd.amount > 0 && wd.amount <= _balances[wd.token], "TokenBridge: not enough amount to withdraw");
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
        require(wd.beneficiary == beneficiary, "TokenBridge: no nft for the beneficiary");
        require(wd.block_number <= block.number, "TokenBridge: not mature enough to withdraw the nft");
        require(wd.status == Status.Withdrawable, "TokenBridge: nft is pending or was already claimed");
        require(_owners[wd.nft][tokenId] != address(0), "TokenBridge: nft token id does not exist or was already claimed");
        wd.status = Status.Withdrawn;
        _owners[wd.nft][tokenId] = address(0);
        wd.nft.safeTransferFrom(address(this), beneficiary, tokenId);
        emit WithdrawalNFT(beneficiary, wd.nft, tokenId);
    }

    function isValidSignatures(uint height, bytes32 hash, bytes[] memory signatures, address[] memory signers) internal view returns (bool) {
        uint _actualSignature = 0;
        uint _requiredSignature = _calculateBFTRequiredNum(validators[height].length);
        address _lastSigner = address(0);
        for (uint i = 0; i < signatures.length; i++) {
            for (uint k = 0; k < signers.length; k++) {
                require(isValidator(height, signers[k]), "TokenBridge: signer is not validator");
                if (_isValidSignature(hash, signatures[i], signers[k])) {
                    _actualSignature++;
                    require(signers[k] > _lastSigner, "TokenBridge: duplicate signature or signers is out of order");
                    _lastSigner = signers[k];
                    break;
                }
            }
        }
        return (_actualSignature >= _requiredSignature);
    }

    function _calculateBFTRequiredNum(uint total) internal pure returns (uint) {
        if (total == 0) return 0;
        return (total - (total - 1) / 3);
    }

    function _isValidSignature(bytes32 hash, bytes memory signature, address signer) internal pure returns (bool) {
        bytes memory prefix = "\x19Ethereum Signed Message:\n32";
        bytes32 prefixedProof = keccak256(abi.encodePacked(prefix, hash));
        return (prefixedProof.recover(signature) == signer || hash.recover(signature) == signer);
    }    
}
