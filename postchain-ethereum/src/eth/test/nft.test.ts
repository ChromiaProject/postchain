import { ethers } from "hardhat";
import chai from "chai";
import { solidity } from "ethereum-waffle";
import { ChrL2__factory, Postchain__factory, EC__factory, MerkleProof__factory, Hash__factory, ERC721Mock__factory } from "../src/types";
import { ChrL2LibraryAddresses } from "../src/types/factories/ChrL2__factory";
import { MerkleProofLibraryAddresses } from "../src/types/factories/MerkleProof__factory";
import { SignerWithAddress } from "@nomiclabs/hardhat-ethers/signers";
import { PostchainLibraryAddresses } from "../src/types/factories/Postchain__factory";
import { BigNumber, ContractReceipt, ContractTransaction } from "ethers";
import { BytesLike, hexZeroPad, keccak256 } from "ethers/lib/utils";
import { DecodeHexStringToByteArray, hashGtvBytes32Leaf, hashGtvBytes64Leaf, postchainMerkleNodeHash } from "./utils"
import { intToHex } from "ethjs-util";


chai.use(solidity);
const { expect } = chai;

describe("Non Fungible Token", () => {
    let nftAddress: string;
    let chrL2Address: string;
    let merkleAddress: string;
    let hasherAddress: string;
    let chrL2Interface: ChrL2LibraryAddresses;
    let merkleProofInterface: MerkleProofLibraryAddresses;
    let directoryNodes: SignerWithAddress;
    let appNodes: SignerWithAddress;
    const name = "CRYPTOPUNKS";
    const symbol = "Ͼ";
    const baseURI = "https://gateway.pinata.cloud/ipfs/QmR5NAV7vCi5oobK2wKNKcM5QAyCCzCg2wysZXwhCYbBLs/";

    beforeEach(async () => {
        const [deployer] = await ethers.getSigners()
        ;[directoryNodes, appNodes] = await ethers.getSigners()
        const tokenFactory = new ERC721Mock__factory(deployer)
        const tokenContract = await tokenFactory.deploy(name, symbol)
        nftAddress = tokenContract.address

        const ecFactory = new EC__factory(deployer);
        const ec = await ecFactory.deploy();
        const hashFactory = new Hash__factory(deployer);
        const hasher = await hashFactory.deploy()
        hasherAddress = hasher.address;
        merkleProofInterface = {"__$d42deddc843410a175fce5315eff8fddf4$__": hasherAddress}
        const merkleFactory = new MerkleProof__factory(merkleProofInterface, deployer)
        const merkle = await merkleFactory.deploy();
        merkleAddress = merkle.address
        const postchainInterface: PostchainLibraryAddresses = {
            "__$2a5a34f5712a9d42d5a557457b5485a829$__": ec.address,
            "__$d42deddc843410a175fce5315eff8fddf4$__": hasherAddress,
            "__$fc40fc502169a2ee9db1b4670a1c17e7ae$__": merkleAddress
        }
        const postchainFactory = new Postchain__factory(postchainInterface, deployer);
        const postchain = await postchainFactory.deploy();
        chrL2Interface = {
            "__$7e4eb5d82fde1ae3468a6b70e42858da4c$__": postchain.address,
            "__$fc40fc502169a2ee9db1b4670a1c17e7ae$__": merkleAddress
        }
        const chrl2Factory = new ChrL2__factory(chrL2Interface, deployer)
        const chrL2Contract = await chrl2Factory.deploy([directoryNodes.address], [appNodes.address])
        chrL2Address = chrL2Contract.address
    });

    describe("Deposit NFT", async () => {
        it("User can deposit NFT to target smartcontract", async () => {
            const [deployer, user] = await ethers.getSigners()
            const tokenInstance = new ERC721Mock__factory(deployer).attach(nftAddress)
            const tokenId = BigNumber.from(8888)

            await tokenInstance.mint(user.address, tokenId)
            expect(await tokenInstance.balanceOf(user.address)).to.eq(1)
            expect(await tokenInstance.ownerOf(tokenId)).to.eq(user.address)

            const chrL2Instance = new ChrL2__factory(chrL2Interface, user).attach(chrL2Address)
            const tokenApproveInstance = new ERC721Mock__factory(user).attach(nftAddress)
            await tokenApproveInstance.setApprovalForAll(chrL2Address, true)
            let tokenURI = await tokenApproveInstance.tokenURI(tokenId)
            expect(tokenURI).to.eq(baseURI+tokenId.toString())
            const expectedPayload = ''.concat(
                "0xa5", "84", "000000e1", "30", "84", "000000db", // Gtv tag, Ber length, Length, Ber tag, Ber length, Value length
                "a1", "16", "04", "14", // Gtv tag, Length, Ber tag, Value length
                user.address.substring(2),
                "a1", "16", "04", "14", // Gtv tag, Length, Ber tag, Value Length
                nftAddress.substring(2),
                "a3", "23", "02", "21", "00", // Gtv tag, Length, Ber tag, Value Length, Zero padding for signed bit
                hexZeroPad(tokenId.toHexString(), 32).substring(2),
                "a2", "84", "00000011", "0c", "84", "0000000b", 
                "43525950544f50554e4b53",
                "a2", "84", "00000008", "0c", "84", "00000002", 
                "cfbe",
                "a2", "84", "0000005b", "0c", "84", "00000055", 
                "68747470733a2f2f676174657761792e70696e6174612e636c6f75642f697066732f516d52354e415637764369356f6f624b32774b4e4b634d3551417943437a4367327779735a587768435962424c732f38383838"
            )            
            await expect(chrL2Instance.depositNFT(nftAddress, tokenId))
                    .to.emit(chrL2Instance, "Deposited")
                    .withArgs(1, expectedPayload.toLowerCase())

            expect(await tokenInstance.balanceOf(chrL2Address)).to.eq(1)
            expect(await tokenInstance.ownerOf(tokenId)).to.eq(chrL2Address)
        })
    })

    describe("Withdraw NFT", async () => {
        it("User can withdraw NFT by providing properly proof data", async () => {
            const [deployer, user] = await ethers.getSigners()
            const tokenInstance = new ERC721Mock__factory(deployer).attach(nftAddress)
            const tokenId = BigNumber.from(8888)

            await tokenInstance.mint(user.address, tokenId)
            expect(await tokenInstance.balanceOf(user.address)).to.eq(1)
            expect(await tokenInstance.ownerOf(tokenId)).to.eq(user.address)

            const chrL2Instance = new ChrL2__factory(chrL2Interface, user).attach(chrL2Address)
            const tokenApproveInstance = new ERC721Mock__factory(user).attach(nftAddress)
            await tokenApproveInstance.setApprovalForAll(chrL2Address, true)
            let tx: ContractTransaction = await chrL2Instance.depositNFT(nftAddress, tokenId)
            let receipt: ContractReceipt = await tx.wait()
            let logs = receipt.events?.filter((x) =>  {return x.event == 'Deposited'})
            if (logs !== undefined) {
                let log = logs[0]
                const blockNumber = hexZeroPad(intToHex(log.blockNumber), 32)
                const serialNumber = hexZeroPad(intToHex(log.blockNumber + log.logIndex), 32)
                const contractAddress = hexZeroPad(nftAddress, 32)
                const toAddress = hexZeroPad(user.address, 32)
                const tokenIdHex = hexZeroPad(tokenId.toHexString(), 32)
                let event: string = ''
                event = event.concat(serialNumber.substring(2, serialNumber.length))
                event = event.concat(contractAddress.substring(2, contractAddress.length))
                event = event.concat(toAddress.substring(2, toAddress.length))
                event = event.concat(tokenIdHex.substring(2, tokenIdHex.length))

                let data = DecodeHexStringToByteArray(event)
                let hashEventLeaf = keccak256(data)
                let hashRootEvent = keccak256(keccak256(hashEventLeaf))
                let state = blockNumber.substring(2, blockNumber.length).concat(event)
                let hashRootState = keccak256(DecodeHexStringToByteArray(state))
                let el2Leaf = hashRootEvent.substring(2, hashRootEvent.length).concat(hashRootState.substring(2, hashRootState.length))

                let blockchainRid = "977dd435e17d637c2c71ebb4dec4ff007a4523976dc689c7bcb9e6c514e4c795"
                let previousBlockRid = "49e46bf022de1515cbb2bf0f69c62c071825a9b940e8f3892acb5d2021832ba0"
                let merkleRootHash = "96defe74f43fcf2d12a1844bcd7a3a7bcb0d4fa191776953dae3f1efb508d866"
                let merkleRootHashHashedLeaf = hashGtvBytes32Leaf(DecodeHexStringToByteArray(merkleRootHash))
                let dependencies = "56bfbee83edd2c9a79ff421c95fc8ec0fa0d67258dca697e47aae56f6fbc8af3"
                let dependenciesHashedLeaf = hashGtvBytes32Leaf(DecodeHexStringToByteArray(dependencies))
                let extraDataMerkleRoot = "138C7AB3DFD4310722D953B4A37D0302C62CE29BD89EBD4517CB1CD2A87659F8"

                let node1 = hashGtvBytes32Leaf(DecodeHexStringToByteArray(blockchainRid))
                let node2 = hashGtvBytes32Leaf(DecodeHexStringToByteArray(previousBlockRid))
                let node12 = postchainMerkleNodeHash([0x00, node1, node2])
                let node3 = hashGtvBytes32Leaf(DecodeHexStringToByteArray(merkleRootHash))
                let node4 = keccak256(DecodeHexStringToByteArray("1629878444220"))
                let node34 = postchainMerkleNodeHash([0x00, node3, node4])
                let node5 = keccak256(DecodeHexStringToByteArray("46"))
                let node6 = hashGtvBytes32Leaf(DecodeHexStringToByteArray(dependencies))
                let node56 = postchainMerkleNodeHash([0x00, node5, node6])
                let node1234 = postchainMerkleNodeHash([0x00, node12, node34])
                let node5678 = postchainMerkleNodeHash([0x00, node56, DecodeHexStringToByteArray(extraDataMerkleRoot)])

                let blockRid = postchainMerkleNodeHash([0x7, node1234, node5678])
                let blockHeader: BytesLike = ''
                blockHeader = blockHeader.concat(blockchainRid, blockRid.substring(2, blockRid.length), previousBlockRid,
                                    merkleRootHashHashedLeaf.substring(2, merkleRootHashHashedLeaf.length),
                                    node4.substring(2, node4.length), node5.substring(2, node5.length),
                                    dependenciesHashedLeaf.substring(2, dependenciesHashedLeaf.length),
                                    extraDataMerkleRoot
                )

                let sig = await appNodes.signMessage(DecodeHexStringToByteArray(blockRid.substring(2, blockRid.length)))

                let merkleProof = [
                    DecodeHexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000"), 
                    DecodeHexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000")
                ]

                let eventProof = {
                    leaf: DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    position: 0,
                    merkleProofs: merkleProof,
                }

                let el2HashedLeaf = hashGtvBytes64Leaf(DecodeHexStringToByteArray(el2Leaf))

                let el2Proof = {
                    el2Leaf: DecodeHexStringToByteArray(el2Leaf),
                    el2HashedLeaf: DecodeHexStringToByteArray(el2HashedLeaf.substring(2, el2HashedLeaf.length)),
                    el2Position: 1,
                    extraRoot: DecodeHexStringToByteArray(extraDataMerkleRoot),
                    extraMerkleProofs: [DecodeHexStringToByteArray("36F5BC29C2E9593F50B0E017700DC775F7F899FEA2FE8CEE8EEA5DDBCD483F0C")],
                }

                // swap toAddress and contractAddress position to make maliciousEvent
                let maliciousEvent: string = ''
                maliciousEvent = maliciousEvent.concat(serialNumber.substring(2, serialNumber.length))
                maliciousEvent = maliciousEvent.concat(toAddress.substring(2, toAddress.length))
                maliciousEvent = maliciousEvent.concat(contractAddress.substring(2, contractAddress.length))
                maliciousEvent = maliciousEvent.concat(tokenIdHex.substring(2, tokenIdHex.length))                
                let maliciousData = DecodeHexStringToByteArray(maliciousEvent)

                await expect(chrL2Instance.withdrawRequestNFT(maliciousData, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], el2Proof)
                ).to.be.revertedWith('Postchain: invalid event')

                // hash two times to make malicious data
                let maliciousHashEventLeaf = keccak256(keccak256(data))
                let invalidEl2Leaf = {
                    el2Leaf: DecodeHexStringToByteArray(el2Leaf),
                    el2HashedLeaf: DecodeHexStringToByteArray(maliciousHashEventLeaf.substring(2, maliciousHashEventLeaf.length)),
                    el2Position: 1,
                    extraRoot: DecodeHexStringToByteArray(extraDataMerkleRoot),
                    extraMerkleProofs: [DecodeHexStringToByteArray("36F5BC29C2E9593F50B0E017700DC775F7F899FEA2FE8CEE8EEA5DDBCD483F0C")],
                }                
                await expect(chrL2Instance.withdrawRequestNFT(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], invalidEl2Leaf)
                ).to.be.revertedWith('Postchain: invalid el2 extra data')

                let invalidExtraDataRoot = {
                    el2Leaf: DecodeHexStringToByteArray(el2Leaf),
                    el2HashedLeaf: DecodeHexStringToByteArray(el2HashedLeaf.substring(2, el2HashedLeaf.length)),
                    el2Position: 1,
                    extraRoot: DecodeHexStringToByteArray("04D17CC3DD96E88DF05A943EC79DD436F220E84BA9E5F35CACF627CA225424A2"),
                    extraMerkleProofs: [DecodeHexStringToByteArray("36F5BC29C2E9593F50B0E017700DC775F7F899FEA2FE8CEE8EEA5DDBCD483F0C")],
                }                
                await expect(chrL2Instance.withdrawRequestNFT(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], invalidExtraDataRoot)
                ).to.be.revertedWith('Postchain: invalid extra data root')

                let maliciousBlockRid = postchainMerkleNodeHash([0x7, node1234, node1234])
                let maliciousBlockHeader: BytesLike = ''
                maliciousBlockHeader = maliciousBlockHeader.concat(blockchainRid, maliciousBlockRid.substring(2, maliciousBlockRid.length), previousBlockRid, 
                                    merkleRootHashHashedLeaf.substring(2, merkleRootHashHashedLeaf.length),
                                    node4.substring(2, node4.length), node5.substring(2, node5.length),
                                    dependenciesHashedLeaf.substring(2, dependenciesHashedLeaf.length),
                                    extraDataMerkleRoot
                )
                await expect(chrL2Instance.withdrawRequestNFT(data, eventProof,
                    DecodeHexStringToByteArray(maliciousBlockHeader),
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], el2Proof)
                ).to.be.revertedWith('Postchain: invalid block header')

                let maliciousEl2Proof = {
                    el2Leaf: DecodeHexStringToByteArray(el2Leaf),
                    el2HashedLeaf: DecodeHexStringToByteArray(el2HashedLeaf.substring(2, el2HashedLeaf.length)),
                    el2Position: 0,
                    extraRoot: DecodeHexStringToByteArray(extraDataMerkleRoot),
                    extraMerkleProofs: [
                        DecodeHexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000"), 
                        DecodeHexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000")                        
                    ],
                }
                await expect(chrL2Instance.withdrawRequestNFT(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], maliciousEl2Proof)
                ).to.be.revertedWith('Postchain: invalid el2 extra merkle proof')

                let maliciousEventProof = {
                    leaf: DecodeHexStringToByteArray(maliciousHashEventLeaf.substring(2, maliciousHashEventLeaf.length)),
                    position: 0,
                    merkleProofs: merkleProof,
                }
                await expect(chrL2Instance.withdrawRequestNFT(data, maliciousEventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], el2Proof)
                ).to.be.revertedWith('ChrL2: invalid merkle proof')

                await expect(chrL2Instance.withdrawRequestNFT(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [], el2Proof)
                ).to.be.revertedWith('ChrL2: block signature is invalid')

                await expect(chrL2Instance.withdrawRequestNFT(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], el2Proof)
                ).to.emit(chrL2Instance, "WithdrawRequestNFT")
                .withArgs(user.address, nftAddress, tokenId)

                await expect(chrL2Instance.withdrawRequestNFT(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], el2Proof)
                ).to.be.revertedWith('ChrL2: event hash was already used')

                await expect(chrL2Instance.withdrawNFT(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    deployer.address)).to.revertedWith("ChrL2: no nft for the beneficiary")

                await expect(chrL2Instance.withdrawNFT(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address)).to.revertedWith("ChrL2: not mature enough to withdraw the nft")

                // force mining 100 blocks
                for (let i = 0; i < 100; i++) {
                    await ethers.provider.send('evm_mine', [])
                }

                expect(await tokenInstance.balanceOf(chrL2Address)).to.eq(1)
                expect(await tokenInstance.ownerOf(tokenId)).to.eq(chrL2Address)

                await expect(chrL2Instance.withdrawNFT(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address))
                .to.emit(chrL2Instance, "WithdrawalNFT")
                .withArgs(user.address, nftAddress, tokenId)

                expect(await tokenInstance.balanceOf(user.address)).to.eq(1)
                expect(await tokenInstance.ownerOf(tokenId)).to.eq(user.address)

                await expect(chrL2Instance.withdrawNFT(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address)).to.revertedWith("ChrL2: nft is pending or was already claimed")
            }
        })
    })
})