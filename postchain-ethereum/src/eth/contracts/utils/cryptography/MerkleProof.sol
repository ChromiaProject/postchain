// SPDX-License-Identifier: GPL-3.0-only
pragma solidity >=0.6.0 <=0.8.7;

import "./Hash.sol";

library MerkleProof {
    /**
     * @dev verify merkle proof using keccak256
     */
    function verify(bytes32[] calldata proofs, bytes32 leaf, uint position, bytes32 rootHash) public pure returns (bool) {
        bytes32 r = leaf;
        for (uint i = 0; i < proofs.length; i++) {
            uint b = position & (1 << i);
            if (b == 0) {
                r = Hash.hash(r, proofs[i]);
            } else {
                r = Hash.hash(proofs[i], r);
            }
        }
        return (r == rootHash);
    }

    /**
     * @dev verify merkle proof using sha256
     */
    function verifySHA256(bytes32[] calldata proofs, bytes32 leaf, uint position, bytes32 rootHash) public pure returns (bool) {
        bytes32 r = leaf;
        for (uint i = 0; i < proofs.length; i++) {
            uint b = position & (1 << i);
            if (b == 0) {
                r = sha256(abi.encodePacked(r, proofs[i]));
            } else {
                r = sha256(abi.encodePacked(proofs[i], r));
            }
        }
        return (r == rootHash);
    }
    
    function root(bytes32[] memory nodes) public pure returns (bytes32) {
        if (nodes.length == 1) return nodes[0];
        uint len = nodes.length/2;
        bytes32[] memory _nodes = new bytes32[](len);
        for (uint i = 0; i < len; i++) {
            _nodes[i] = Hash.hash(nodes[i*2], nodes[i*2+1]);
        }
        return root(_nodes);
    }
}