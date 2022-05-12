// SPDX-License-Identifier: GPL-3.0-only
pragma solidity ^0.8.0;

library Gtv {

    // GTV BER tags
    uint8 constant GTV_BYTE_ARRAY_BER_TAG = 0xA1;
    uint8 constant GTV_STRING_BER_TAG = 0xA2;
    uint8 constant GTV_BIG_INT_BER_TAG = 0xA6;
    uint8 constant GTV_ARRAY_BER_TAG = 0xA5;

    // Universal BER tags
    uint8 constant INT_BER_TAG = 2;
    uint8 constant OCTET_STRING_BER_TAG = 4;
    uint8 constant UTF8_STRING_BER_TAG = 12;
    uint8 constant ARRAY_BER_TAG = 48;

    uint8 constant BER_LENGTH_MASK = 0x80;

    function encodeArray(bytes memory args) internal pure returns (bytes memory) {
        // This restriction is imposed on us by BER length format, but really it would be silly to send anything that large
        require(args.length < 2 ** 31 - 6, "Too large argument array");
        return abi.encodePacked(
            GTV_ARRAY_BER_TAG,
            uint8(BER_LENGTH_MASK + 4), // Length arg is uint32 = 4 bytes
            uint32(args.length + 6),    // In addition to args.length: uint8 + uint8 + uint32 = 6 bytes
            ARRAY_BER_TAG,
            uint8(BER_LENGTH_MASK + 4), // Length arg is uint32 = 4 bytes
            uint32(args.length),
            args
        );
    }

    function encode(uint256 value) internal pure returns (bytes memory) {
        return abi.encodePacked(
            GTV_BIG_INT_BER_TAG,
            uint8(35),   // uint8 + uint8 + uint8 + uint256 = 35 bytes
            INT_BER_TAG,
            uint8(33),   // uint8 + uint256 = 33 bytes
            uint8(0),    // Ugly solution to convert to signed format
            value
        );
    }

    function encode(address value) internal pure returns (bytes memory) {
        return abi.encodePacked(
            GTV_BYTE_ARRAY_BER_TAG,
            uint8(22),              // uint8 + uint8 + 20 = 22 bytes
            OCTET_STRING_BER_TAG,
            uint8(20),              // Address is 20 bytes in solidity
            value
        );
    }

    function encode(string memory value) internal pure returns (bytes memory) {
        // This restriction is imposed on us by BER length format, but really it would be silly to send anything that large
        uint length = bytes(value).length;
        require(length < 2 ** 31 - 6, "Too large string");
        return abi.encodePacked(
            GTV_STRING_BER_TAG,
            uint8(BER_LENGTH_MASK + 4), // Length arg is uint32 = 4 bytes
            uint32(length + 6),         // In addition to length: uint8 + uint8 + uint32 = 6 bytes
            UTF8_STRING_BER_TAG,
            uint8(BER_LENGTH_MASK + 4), // Length arg is uint32 = 4 bytes
            uint32(length),
            value
        );
    }

}