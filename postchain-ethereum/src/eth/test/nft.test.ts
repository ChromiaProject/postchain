import { ethers } from "hardhat";
import chai from "chai";
import { solidity } from "ethereum-waffle";
import { ChrL2__factory, Postchain__factory, EC__factory, MerkleProof__factory, Hash__factory, ERC721Mock__factory } from "../src/types";
import { ChrL2LibraryAddresses } from "../src/types/factories/ChrL2__factory";
import { MerkleProofLibraryAddresses } from "../src/types/factories/MerkleProof__factory";
import { SignerWithAddress } from "@nomiclabs/hardhat-ethers/signers";
import { PostchainLibraryAddresses } from "../src/types/factories/Postchain__factory";


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

    describe("Deposit", async () => {
        it("User can deposit NFT to target smartcontract", async () => {
            const [deployer, user] = await ethers.getSigners()
            const tokenInstance = new ERC721Mock__factory(deployer).attach(nftAddress)
            const tokenId = 8888

            await tokenInstance.mint(user.address, tokenId)
            expect(await tokenInstance.balanceOf(user.address)).to.eq(1)
            expect(await tokenInstance.ownerOf(tokenId)).to.eq(user.address)

            const chrL2Instance = new ChrL2__factory(chrL2Interface, user).attach(chrL2Address)
            const tokenApproveInstance = new ERC721Mock__factory(user).attach(nftAddress)
            await tokenApproveInstance.setApprovalForAll(chrL2Address, true)
            let tokenURI = await tokenApproveInstance.tokenURI(tokenId)
            expect(tokenURI).to.eq(baseURI+tokenId.toString())
            await expect(chrL2Instance.depositNFT(nftAddress, tokenId))
                    .to.emit(chrL2Instance, "DepositedNFT")
                    .withArgs(user.address, nftAddress, tokenId, name, symbol, tokenURI)

            expect(await tokenInstance.balanceOf(chrL2Address)).to.eq(1)
            expect(await tokenInstance.ownerOf(tokenId)).to.eq(chrL2Address)
        })
    })
})