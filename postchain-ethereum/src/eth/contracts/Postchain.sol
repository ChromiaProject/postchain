// SPDX-License-Identifier: GPL-3.0-only
pragma solidity >=0.6.0 <=0.8.7;
pragma experimental ABIEncoderV2;

import "./utils/cryptography/Hash.sol";
import "./utils/cryptography/ECDSA.sol";
import "./utils/cryptography/MerkleProof.sol";
import "./token/ERC20.sol";

library Postchain {
    using EC for bytes32;
    using MerkleProof for bytes32[];

    bytes32 constant L2_EVENT_KEY = 0x1F1831C339CD7E1195B64253AF6691E58A43D402BE48D0834BBD1869A9C9C935;
    bytes32 constant L2_STATE_KEY = 0x04A48CDA5CE81FF2A97A9E2C0F521C2853258D6DDBA62190D3F0A2523B09C4B0;    

    struct Event {
        uint256 serialNumber;
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

    function isValidNodes(bytes32 hash, address[] memory nodes) public pure returns (bool) {
        uint len = _upperPowerOfTwo(nodes.length);
        bytes32[] memory _nodes = new bytes32[](len);
        for (uint i = 0; i < nodes.length; i++) {
            _nodes[i] = keccak256(abi.encodePacked(nodes[i]));
        }
        for (uint i = nodes.length; i < len; i++) {
            _nodes[i] = 0x0;
        }
        return _nodes.root() == hash;
    }

    function isValidSignatures(bytes32 hash, bytes[] memory signatures, address[] memory signers) public pure returns (bool) {
        uint _actualSignature = 0;
        uint _requiredSignature = _calculateBFTRequiredNum(signers.length);
        for (uint i = 0; i < signatures.length; i++) {
            for (uint k = 0; k < signers.length; k++) {
                if (_isValidSignature(hash, signatures[i], signers[k])) {
                    _actualSignature++;
                    break;
                }
            }
        }
        return (_actualSignature >= _requiredSignature);
    }

    function verifyEvent(bytes32 _hash, bytes calldata _event) public pure returns (ERC20, address, uint256) {
        Event memory evt = abi.decode(_event, (Event));
        bytes32 hash = keccak256(_event);
        if (hash != _hash) {
            revert('Postchain: invalid event');
        }
        return (evt.token, evt.beneficiary, evt.amount);
    }

    function verifyBlock(bytes32 _hash,
        bytes calldata blockHeader,
        bytes[] calldata sigs,
        bytes32[] calldata merkleProofs,
        uint position,
        address[] memory _appNode
    ) public pure {
        (bytes32 blockRid, bytes32 eventRoot, ) = _verifyBlockHeader(blockHeader);
        if (!isValidSignatures(blockRid, sigs, _appNode)) revert("Postchain: block signature is invalid");
        if (!merkleProofs.verify(_hash, position, eventRoot)) revert("Postchain: invalid merkle proof");
    }

    function _verifyBlockHeader(bytes calldata blockHeader) internal pure returns (bytes32, bytes32, bytes32) {

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

        if (blockRid != header.blockRid) revert("Postchain: invalid block header");
        return (blockRid, header.l2RootEvent, header.l2RootState);
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

   function _upperPowerOfTwo(uint x) internal pure returns (uint) {
        uint p = 1;
        while (p < x) p <<= 1;
        return p;
    }
}