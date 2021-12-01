// SPDX-License-Identifier: GPL-3.0-only
pragma solidity >=0.6.0 <=0.8.7;
pragma experimental ABIEncoderV2;

library Data {
    struct EL2ProofData {
        bytes32 leaf;
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