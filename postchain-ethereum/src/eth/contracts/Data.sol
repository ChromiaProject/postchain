// SPDX-License-Identifier: GPL-3.0-only
pragma solidity ^0.8.0;

library Data {
    struct EL2ProofData {
        bytes el2Leaf;
        bytes32 el2HashedLeaf;
        uint el2Position;
        bytes32 extraRoot;
        bytes32[] extraMerkleProofs;
    }

    struct EventProof {
        bytes32 leaf;
        uint position;
        bytes32[] merkleProofs;
    }
}