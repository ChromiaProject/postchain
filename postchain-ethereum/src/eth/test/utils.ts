import { ethers } from "hardhat";
import { BytesLike } from "ethers/lib/utils";

export function DecodeHexStringToByteArray(hexString: string) {
    var result = [];
    while (hexString.length >= 2) { 
        result.push(parseInt(hexString.substring(0, 2), 16))
        hexString = hexString.substring(2, hexString.length)
    }
    return result;
}

export function postchainMerkleNodeHash(values: any[]): string {
    return ethers.utils.soliditySha256(['uint8', 'bytes32', 'bytes32'], values)
}

export function hashGtvBytes32Leaf (data: BytesLike): string {
    var result: string = ''
    result = ethers.utils.soliditySha256(['uint8', 'uint8', 'uint8', 'uint8', 'uint8', 'bytes32'], [0x1, 0xA1, 32+2, 0x4, 32, data])
    return result
}

export function hashGtvBytes64Leaf (data: BytesLike): string {
    var result: string = ''
    result = ethers.utils.soliditySha256(['uint8', 'uint8', 'uint8', 'uint8', 'uint8', 'bytes'], [0x1, 0xA1, 64+2, 0x4, 64, data])
    return result
}