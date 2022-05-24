// SPDX-License-Identifier: GPL-3.0-only
pragma solidity ^0.8.0;

// Interfaces
import "@openzeppelin/contracts/interfaces/IERC721.sol";
import "@openzeppelin/contracts/interfaces/IERC20.sol";

// Internal libraries
import "./utils/cryptography/Hash.sol";
import "./utils/cryptography/ECDSA.sol";
import "./utils/cryptography/MerkleProof.sol";
import "./Data.sol";

library Postchain {
    using MerkleProof for bytes32[];

    struct Event {
        uint256 serialNumber;
        IERC20 token;
        address beneficiary;
        uint256 amount;
    }

    struct EventNFT {
        uint256 serialNumber;
        IERC721 nft;
        address beneficiary;
        uint256 tokenId;
    }

    struct BlockHeaderData {
        bytes32 blockchainRid;
        bytes32 blockRid;
        bytes32 previousBlockRid;
        bytes32 merkleRootHashHashedLeaf;
        bytes32 timestampHashedLeaf;
        bytes32 heightHashedLeaf;
        bytes32 dependenciesHashedLeaf;
        bytes32 extraDataHashedLeaf;
    }



    function verifyEvent(bytes32 _hash, bytes memory _event) internal pure returns (IERC20, address, uint256) {
        Event memory evt = abi.decode(_event, (Event));
        bytes32 hash = keccak256(_event);
        if (hash != _hash) {
            revert('Postchain: invalid event');
        }
        return (evt.token, evt.beneficiary, evt.amount);
    }

    function verifyEventNFT(bytes32 _hash, bytes memory _event) internal pure returns (IERC721, address, uint256) {
        EventNFT memory evt = abi.decode(_event, (EventNFT));
        bytes32 hash = keccak256(_event);
        if (hash != _hash) {
            revert('Postchain: invalid event');
        }
        return (evt.nft, evt.beneficiary, evt.tokenId);
    }    

    function verifyBlockHeader(
        bytes memory blockHeader,
        Data.EL2ProofData memory proof
    ) internal pure returns (bytes32, bytes32, bytes32) {
        require(Hash.hashGtvBytes64Leaf(proof.el2Leaf) == proof.el2HashedLeaf, "Postchain: invalid el2 extra data");
        (bytes32 blockRid, bytes32 extraDataHashedLeaf) = _decodeBlockHeader(blockHeader);
        require(proof.extraRoot == extraDataHashedLeaf, "Postchain: invalid extra data root");
        if (!proof.extraMerkleProofs.verifySHA256(proof.el2HashedLeaf, proof.el2Position, proof.extraRoot)) {
            revert("Postchain: invalid el2 extra merkle proof");
        }
        return (blockRid, _bytesToBytes32(proof.el2Leaf, 0), _bytesToBytes32(proof.el2Leaf, 32));
    }

    function _decodeBlockHeader(
        bytes memory blockHeader
    ) internal pure returns (bytes32, bytes32) {
        BlockHeaderData memory header = abi.decode(blockHeader, (BlockHeaderData));

        bytes32 node12 = sha256(abi.encodePacked(
                uint8(0x00),
                Hash.hashGtvBytes32Leaf(header.blockchainRid),
                Hash.hashGtvBytes32Leaf(header.previousBlockRid)
            ));

        bytes32 node34 = sha256(abi.encodePacked(
                uint8(0x00),
                header.merkleRootHashHashedLeaf,
                header.timestampHashedLeaf
            ));

        bytes32 node56 = sha256(abi.encodePacked(
                uint8(0x00),
                header.heightHashedLeaf,
                header.dependenciesHashedLeaf
            ));

        bytes32 node1234 = sha256(abi.encodePacked(
                uint8(0x00),
                node12,
                node34
            ));

        bytes32 node5678 = sha256(abi.encodePacked(
                uint8(0x00),
                node56,
                header.extraDataHashedLeaf
            ));

        bytes32 blockRid = sha256(abi.encodePacked(
                uint8(0x7), // Gtv merkle tree Array Root Node prefix
                node1234,
                node5678
            ));

        if (blockRid != header.blockRid) revert("Postchain: invalid block header");
        return (blockRid, header.extraDataHashedLeaf);
    }

    function _bytesToBytes32(bytes memory b, uint offset) internal pure returns (bytes32) {
        bytes32 out;

        for (uint i = 0; i < 32; i++) {
            out |= bytes32(b[offset + i] & 0xFF) >> (i * 8);
        }
        return out;
    }
}