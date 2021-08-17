// SPDX-License-Identifier: GPL-3.0-only
pragma solidity >=0.6.0 <=0.8.7;
pragma experimental ABIEncoderV2;

import "./utils/cryptography/Hash.sol";
import "./utils/cryptography/ECDSA.sol";
import "./utils/cryptography/MerkleProof.sol";
import "./ERC20.sol";

contract ChrL2 {
    using EC for bytes32;
    using MerkleProof for bytes32[];

    bytes32 constant L2_EVENT_KEY = 0x1F1831C339CD7E1195B64253AF6691E58A43D402BE48D0834BBD1869A9C9C935;

    bytes32 constant L2_STATE_KEY = 0x04A48CDA5CE81FF2A97A9E2C0F521C2853258D6DDBA62190D3F0A2523B09C4B0;

    mapping (address => mapping(ERC20 => uint256)) public _balances;
    mapping (bytes32 => Withdraw) public _withdraw;
    address[] public directoryNodes;
    address[] public appNodes;

    // Each postchain event will be used to claim only one time.
    mapping (bytes32 => bool) private _events;

    struct Event {
        uint256 blockNumber;
        bytes32 blockHash;
        bytes32 tnxHash;
        uint256 logIndex;
        ERC20 token;
        address beneficiary;
        uint256 amount;
    }

    struct BlockHeaderData {
        bytes32 blockchainRid;
        bytes32 blockRid;
        bytes32 previousBlockRid;
        bytes32 merkleRootHashHashedLeaf;
        uint timestamp;
        uint height;
        bytes32 dependenciesHashedLeaf;
        bytes32 l2RootEvent;
        bytes32 l2RootState;
    }

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

    function updateDirectoryNodes(bytes32 hash, bytes[] memory sigs, address[] memory _directoryNodes) public returns (bool) {
        if (!isValidNodes(hash, _directoryNodes)) revert("ChrL2: Invalid directory node");
        uint BFTRequiredNum = _calculateBFTRequiredNum(directoryNodes.length);
        if (!_isValidSignatures(BFTRequiredNum, hash, sigs, directoryNodes)) revert("ChrL2: Not enough require signature");
        for (uint i = 0; i < directoryNodes.length; i++) {
            directoryNodes.pop();
        }
        directoryNodes = _directoryNodes;
        return true;
    }

    function updateAppNodes(bytes32 hash, bytes[] memory sigs, address[] memory _appNodes) public returns (bool) {
        if (!isValidNodes(hash, _appNodes)) revert("ChrL2: Invalid app node");
        uint BFTRequiredNum = _calculateBFTRequiredNum(directoryNodes.length);
        if (!_isValidSignatures(BFTRequiredNum, hash, sigs, directoryNodes)) revert("ChrL2: Not enough require signature");
        for (uint i = 0; i < appNodes.length; i++) {
            appNodes.pop();
        }
        appNodes = _appNodes;
        return true;
    }

    function isValidNodes(bytes32 hash, address[] memory nodes) public pure returns (bool) {
        uint len = upperPowerOfTwo(nodes.length);
        bytes32[] memory _nodes = new bytes32[](len);
        for (uint i = 0; i < nodes.length; i++) {
            _nodes[i] = keccak256(abi.encodePacked(nodes[i]));
        }
        for (uint i = nodes.length; i < len; i++) {
            _nodes[i] = 0x0;
        }
        return _nodes.root() == hash;
    }

    function upperPowerOfTwo(uint x) public pure returns (uint) {
        uint p = 1;
        while (p < x) p <<= 1;
        return p;
    }

    function _isValidSignatures(uint requiredSignature, bytes32 hash, bytes[] memory signatures, address[] memory signers) internal pure returns (bool) {
        uint _actualSignature = 0;
        for (uint i = 0; i < signatures.length; i++) {
            for (uint k = 0; k < signers.length; k++) {
                if (_isValidSignature(hash, signatures[i], signers[k])) {
                    _actualSignature++;
                    break;
                }
            }
        }
        return (_actualSignature >= requiredSignature);
    }

    function _isValidSignature(bytes32 hash, bytes memory signature, address signer) internal pure returns (bool) {
        bytes memory prefix = "\x19Ethereum Signed Message:\n32";
        bytes32 prefixedProof = keccak256(abi.encodePacked(prefix, hash));
        return (prefixedProof.recover(signature) == signer || hash.recover(signature) == signer);
    }

    function _calculateBFTRequiredNum(uint total) internal pure returns (uint) {
        if (total == 0) return 0;
        return (total - (total - 1) / 3);
    }

    function deposit(ERC20 token, uint256 amount) public returns (bool) {
        token.transferFrom(msg.sender, address(this), amount);
        _balances[msg.sender][token] += amount;
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
        _verify(_hash, blockHeader, sigs, merkleProofs, position);
        (ERC20 token, address beneficiary, uint256 amount) = verifyEventHash(_event, _hash);
        require(amount <= _balances[beneficiary][token], "ChrL2: Not enough amount");
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

    function _verify(bytes32 _hash,
        bytes calldata blockHeader,
        bytes[] calldata sigs,
        bytes32[] calldata merkleProofs,
        uint position
    ) internal view {
        (bytes32 blockRid, bytes32 eventRoot, ) = verifyBlockHeader(blockHeader);
        if (!_isValidSignatures(_calculateBFTRequiredNum(appNodes.length), blockRid, sigs, appNodes)) revert("block signature is invalid");
        if (!merkleProofs.verify(_hash, position, eventRoot)) revert("invalid event merkle proof");
    }

    function withdraw(bytes32 _hash, address payable beneficiary) public {
        Withdraw storage wd = _withdraw[_hash];
        require(wd.beneficiary == beneficiary, "ChrL2: no fund for the beneficiary");
        require(wd.block_number <= block.number, "ChrL2: no mature enough to withdraw the fund");
        require(wd.isWithdraw == false, "ChrL2: fund was already claimed");
        require(wd.amount > 0 && wd.amount <= _balances[beneficiary][wd.token], "ChrL2: Not enough amount to withdraw");
        wd.isWithdraw = true;
        uint value = wd.amount;
        wd.amount = 0;
        _balances[beneficiary][wd.token] -= value;
        wd.token.transfer(beneficiary, value);
        emit Withdrawal(beneficiary, wd.token, value);
    }

    function verifyBlockHeader(bytes calldata blockHeader) public pure returns (bytes32, bytes32, bytes32) {

        BlockHeaderData memory header = abi.decode(blockHeader, (BlockHeaderData));

        bytes32 node12 = sha256(abi.encodePacked(
                uint8(0x00),
                Hash.hashGtvBytes32Leaf(header.blockchainRid),
                Hash.hashGtvBytes32Leaf(header.previousBlockRid)
            ));

        bytes32 node34 = sha256(abi.encodePacked(
                uint8(0x00),
                header.merkleRootHashHashedLeaf,
                Hash.hashGtvIntegerLeaf(header.timestamp)
            ));

        bytes32 node56 = sha256(abi.encodePacked(
                uint8(0x00),
                Hash.hashGtvIntegerLeaf(header.height),
                header.dependenciesHashedLeaf
            ));

        bytes32 l2event = sha256(abi.encodePacked(
                uint8(0x00),
                L2_EVENT_KEY,
                Hash.hashGtvBytes32Leaf(header.l2RootEvent)
            ));

        bytes32 l2state = sha256(abi.encodePacked(
                uint8(0x00),
                L2_STATE_KEY,
                Hash.hashGtvBytes32Leaf(header.l2RootState)
            ));

        bytes32 node78 = sha256(abi.encodePacked(
                uint8(0x8), // Gtv merkle tree dict prefix
                l2event,
                l2state
            ));

        bytes32 node1234 = sha256(abi.encodePacked(
                uint8(0x00),
                node12,
                node34
            ));

        bytes32 node5678 = sha256(abi.encodePacked(
                uint8(0x00),
                node56,
                node78
            ));

        bytes32 blockRid = sha256(abi.encodePacked(
                uint8(0x7), // Gtv merkle tree Array Root Node prefix
                node1234,
                node5678
            ));

        if (blockRid != header.blockRid) revert("invalid block header");
        return (blockRid, header.l2RootEvent, header.l2RootState);
    }

    function verifyEventHash(bytes calldata _event, bytes32 _hash) public pure returns (ERC20, address, uint256) {
        Event memory evt = abi.decode(_event, (Event));
        bytes32 hash = keccak256(_event);
        if (hash != _hash) {
            revert('invalid event');
        }
        return (evt.token, evt.beneficiary, evt.amount);
    }
}
