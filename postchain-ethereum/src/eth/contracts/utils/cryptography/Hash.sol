// SPDX-License-Identifier: GPL-3.0-only
pragma solidity >=0.6.0 <=0.8.7;

library Hash {

    function hash(bytes32 left, bytes32 right) public pure returns (bytes32) {
        if (left == 0x0 && right == 0x0) {
            return 0x0;
        } else if (left == 0x0) {
            return keccak256(abi.encodePacked(right));
        } else if (right == 0x0) {
            return keccak256(abi.encodePacked(left));
        } else {
            return keccak256(abi.encodePacked(left, right));
        }
    }

    function hashGtvBytes32Leaf(bytes32 value) public pure returns (bytes32) {
        return sha256(abi.encodePacked(
                uint8(0x1),  // Gtv merkle tree leaf prefix
                uint8(0xA1), // // Gtv ByteArray tag: CONTEXT_CLASS, CONSTRUCTED, 1
                uint8(32 + 2),
                uint8(0x4), // DER ByteArray tag
                uint8(32),
                value
        ));
    }

    function hashGtvBytes64Leaf(bytes memory value) public pure returns (bytes32) {
        return sha256(abi.encodePacked(
                uint8(0x1),  // Gtv merkle tree leaf prefix
                uint8(0xA1), // // Gtv ByteArray tag: CONTEXT_CLASS, CONSTRUCTED, 1
                uint8(64 + 2),
                uint8(0x4), // DER ByteArray tag
                uint8(64),
                value
        ));
    }
}