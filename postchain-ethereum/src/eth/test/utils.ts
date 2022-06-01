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

export var hashGtvIntegerLeaf = function (num: number): string {
    var result: string = ''
    let nbytes = 1
    let remainingValue = Math.trunc(num / 256)
    while (remainingValue > 0) {
        nbytes += 1
        remainingValue = Math.trunc(remainingValue / 256)
    }
    let b = new Uint8Array(nbytes)
    remainingValue = num
    for (let i = 1; i <= nbytes; i++) {
        let v = remainingValue & 0xFF
        b[nbytes - i] = v
        remainingValue = Math.trunc(remainingValue / 256)
    }
    if ((b[0] & 0x80) > 0) {
        nbytes += 1
        let a = new Uint8Array(1)
        a[0] = 0
        return ethers.utils.soliditySha256(['uint8', 'uint8', 'uint8', 'uint8', 'uint8', 'bytes'], [0x1, 0xA3, nbytes+2, 0x2, nbytes, new Uint8Array([...a, ...b])])
    }
    return ethers.utils.soliditySha256(['uint8', 'uint8', 'uint8', 'uint8', 'uint8', 'bytes'], [0x1, 0xA3, nbytes+2, 0x2, nbytes, b])
}
