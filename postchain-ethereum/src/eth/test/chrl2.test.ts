import { ethers } from "hardhat";
import chai from "chai";
import { solidity } from "ethereum-waffle";
import { TestToken__factory, ChrL2__factory, Postchain__factory, EC__factory, MerkleProof__factory, Hash__factory } from "../src/types";
import { ChrL2LibraryAddresses } from "../src/types/factories/ChrL2__factory";
import { MerkleProofLibraryAddresses } from "../src/types/factories/MerkleProof__factory";
import { SignerWithAddress } from "@nomiclabs/hardhat-ethers/signers";
import { BytesLike, hexZeroPad, keccak256 } from "ethers/lib/utils";
import { PostchainLibraryAddresses } from "../src/types/factories/Postchain__factory";
import { ContractReceipt, ContractTransaction } from "ethers";
import { intToHex } from "ethjs-util";

chai.use(solidity);
const { expect } = chai;

describe("ChrL2", () => {
    let tokenAddress: string;
    let chrL2Address: string;
    let merkleAddress: string;
    let hasherAddress: string;
    let chrL2Interface: ChrL2LibraryAddresses;
    let merkleProofInterface: MerkleProofLibraryAddresses;
    let directoryNodes: SignerWithAddress;
    let appNodes: SignerWithAddress;

    beforeEach(async () => {
        const [deployer] = await ethers.getSigners()
        ;[directoryNodes, appNodes] = await ethers.getSigners()
        const tokenFactory = new TestToken__factory(deployer)
        const tokenContract = await tokenFactory.deploy()
        tokenAddress = tokenContract.address
        expect(await tokenContract.totalSupply()).to.eq(0)

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

    describe("Utility", async () => {
        describe("SHA3", async () => {
            it("Non-empty node sha3 hash function", async () => {
                const [everyone] = await ethers.getSigners()
                const hashInstance = new Hash__factory(everyone).attach(hasherAddress)
                expect(await hashInstance.hash("0x24860e5aba544f2344ca0f3b285c33e7b442e2f2c6d47d4b70dddce79df17f20", 
                                                "0x6d8f6f192029b21aedeaa1107974ea6f21c17e071e0ad1268cef4bf16e72772d"))
                                                    .to.eq("0x0cf42c3b43ad0c84c02c3e553520261b5650ece5ed65bb79a07592f586637f6a");
            })
    
            it("Right empty node sha3 hash function", async () => {
                const [everyone] = await ethers.getSigners()
                const hashInstance = new Hash__factory(everyone).attach(hasherAddress)
                expect(await hashInstance.hash("0x6c5efa1707c93140989e0f95b9a0b8616e0c8ef51392617bf9c917aff96ef769", 
                                                    "0x0000000000000000000000000000000000000000000000000000000000000000"))
                                                    .to.eq("0x48febd01a647789e62260070b31361f1b12a0fe90bc7ebb700b511b12b9ca410");
            })
    
            it("Left empty node sha3 hash function", async () => {
                const [everyone] = await ethers.getSigners()
                const hashInstance = new Hash__factory(everyone).attach(hasherAddress)
                expect(await hashInstance.hash("0x0000000000000000000000000000000000000000000000000000000000000000", 
                                                    "0x6c5efa1707c93140989e0f95b9a0b8616e0c8ef51392617bf9c917aff96ef769"))
                                                    .to.eq("0x48febd01a647789e62260070b31361f1b12a0fe90bc7ebb700b511b12b9ca410");
            })

            it("All empty node", async () => {
                const [everyone] = await ethers.getSigners()
                const hashInstance = new Hash__factory(everyone).attach(hasherAddress)
                expect(await hashInstance.hash("0x0000000000000000000000000000000000000000000000000000000000000000", 
                                                    "0x0000000000000000000000000000000000000000000000000000000000000000"))
                                                    .to.eq("0x0000000000000000000000000000000000000000000000000000000000000000");                
            })
        })

        describe("Merkle Proof", async () => {
            it("Verify valid merkle proof properly", async () => {
                const [everyone] = await ethers.getSigners()
                const merkleInstance = new MerkleProof__factory(merkleProofInterface, everyone).attach(merkleAddress)

                expect(await merkleInstance.verify(["0x57abe736cc8dcd7497b22ba39c7c2009088136d479e23cb7d1526751995832d6", 
                                                            "0xd103842c6a7267b533021131520f29734b4cd2256ea3851aa963339c9d763904"], 
                                                            "0xcb91922c1d21bea083e4c8689dcd0e8af187e672e8aa63a7af4032971318f7f3", 
                                                            0, 
                                                            "0x534a672f017938f18e96f552b0086f7e40ed416ab033ff439c89b75c85d9c638")).to.be.true;
            })

            it("Invalid merkle proof", async () => {
                const [everyone] = await ethers.getSigners()
                const merkleInstance = new MerkleProof__factory(merkleProofInterface, everyone).attach(merkleAddress)

                expect(await merkleInstance.verify(["0x57abe736cc8dcd7497b22ba39c7c2009088136d479e23cb7d1526751995832d6", 
                                                            "0xd103842c6a7267b533021131520f29734b4cd2256ea3851aa963339c9d763904"], 
                                                            "0xcb91922c1d21bea083e4c8689dcd0e8af187e672e8aa63a7af4032971318f7f3", 
                                                            1, 
                                                            "0x534a672f017938f18e96f552b0086f7e40ed416ab033ff439c89b75c85d9c638")).to.be.false;
            })
        })

        describe("SHA256 Merkle Proof", async () => {
            it("Verify valid SHA256 merkle proof properly", async () => {
                const [everyone] = await ethers.getSigners()
                const merkleInstance = new MerkleProof__factory(merkleProofInterface, everyone).attach(merkleAddress)

                expect(await merkleInstance.verifySHA256(["0xA5C806BAC2FE5EE9B94C853516BE3ACD6C24EC8E3E7F468D1D5F0494D96BBEAD"], 
                                                        "0x2A025E08855DB73B6B696E234238F758B287A05B70E77683180249A3E6B83CB6", 
                                                        0, 
                                                        "0xCC83016CFB173EBB09D25C6DB68912882142D800F4A1E29B9A8DAB3F6A663EEC")).to.be.true;

                expect(await merkleInstance.verifySHA256([
                                                            "0x1AC31D93DC1ED9234E3CB37A914577307B8E6BD278081747F45C2E68A7A683F7",
                                                            "0xBCA9E961400F9CEC29BF56A4B72C599E691C66C401F38BE08962B0181475C1D2",
                                                            "0x05458528249F5FCDC287D5691098766CEC2C56AD8B4F0062E2E88EE509E2434B",
                                                            "0xAF0E006FF87C7162DFE509F57E5AF8AC5D7AE460FD7BFD149D6FC587BD314F90"
                                                        ], 
                                                        "0xF43F729546C85598FD7F7FC62D56A30A90B997437F931F0C99639DA271537ED0", 
                                                        6, 
                                                        "0xA87CA2938A45DCAE3954023977E174946FE4251B6E8D43C7ABB49F91F208F3D4")).to.be.true;
            })

            it("Invalid SHA256 merkle proof", async () => {
                const [everyone] = await ethers.getSigners()
                const merkleInstance = new MerkleProof__factory(merkleProofInterface, everyone).attach(merkleAddress)

                expect(await merkleInstance.verifySHA256(["0xA5C806BAC2FE5EE9B94C853516BE3ACD6C24EC8E3E7F468D1D5F0494D96BBEAD"], 
                                                        "0x2A025E08855DB73B6B696E234238F758B287A05B70E77683180249A3E6B83CB6", 
                                                        1, // wrong proof leaf position
                                                        "0xCC83016CFB173EBB09D25C6DB68912882142D800F4A1E29B9A8DAB3F6A663EEC")).to.be.false;

                expect(await merkleInstance.verifySHA256([
                                                            "0x1AC31D93DC1ED9234E3CB37A914577307B8E6BD278081747F45C2E68A7A683F7",
                                                            "0xBCA9E961400F9CEC29BF56A4B72C599E691C66C401F38BE08962B0181475C1D2",
                                                            "0x05458528249F5FCDC287D5691098766CEC2C56AD8B4F0062E2E88EE509E2434B",
                                                            "0xAF0E006FF87C7162DFE509F57E5AF8AC5D7AE460FD7BFD149D6FC587BD314F90"
                                                        ], 
                                                        "0xF43F729546C85598FD7F7FC62D56A30A90B997437F931F0C99639DA271537ED0", 
                                                        5, // wrong proof leaf position
                                                        "0xA87CA2938A45DCAE3954023977E174946FE4251B6E8D43C7ABB49F91F208F3D4")).to.be.false;
            })
        })        
    })

    describe("Nodes", async () => {
        it("Invalid directory node: invalid input data", async () => {
            const [anyone] = await ethers.getSigners()
            const chrL2Instance = new ChrL2__factory(chrL2Interface, anyone).attach(chrL2Address)

            // Directory Nodes
            await expect(chrL2Instance.updateDirectoryNodes("0x01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdd", 
                                                    ["0x8d3c78fb047f38e1b9a1b74f0695f0e494e5924b239c94597e3bbf9ec405bfb35ee015d470c7259be0bcfa559b6e83202f1f8dee01c01d96d2566a11a83d0f771c"], 
                                                    ["0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3"]))
                    .to.be.revertedWith("ChrL2: invalid directory node")
        })

        it("Invalid directory node: not enough signature", async () => {
            const [anyone] = await ethers.getSigners()
            const chrL2Instance = new ChrL2__factory(chrL2Interface, anyone).attach(chrL2Address)
            expect(await chrL2Instance.directoryNodes(0)).to.eq(directoryNodes.address)
            let sig = await directoryNodes.signMessage(DecodeHexStringToByteArray("01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdc"))
            // Directory Nodes
            await chrL2Instance.updateDirectoryNodes("0x01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdc", 
                                                    [sig],
                                                    ["0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3"])

            await expect(chrL2Instance.updateDirectoryNodes("0x01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdc", 
                                                    [],
                                                    ["0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3"]))
                    .to.be.revertedWith("ChrL2: not enough require signature")
        })

        it("Invalid app node: invalid input data", async () => {
            const [anyone] = await ethers.getSigners()
            const chrL2Instance = new ChrL2__factory(chrL2Interface, anyone).attach(chrL2Address)

            // App Nodes
            await expect(chrL2Instance.updateAppNodes("0x7fcc39140a3e49a5950393cbe3d7e063adb8ede85106a1a7f3e3e610585d1c5b", 
                                                ["0xf9d0a80283aa2a1088adafd4e99453be610a9fd61c69d40b36deab48c84b54d47895765b82d9ae442a00671d462d5a83b34e8bc5fb9c64a9e453f8f18be68f381b"], 
                                                [
                                                    "0x659e4a3726275edFD125F52338ECe0d54d15BD99", 
                                                    "0x75e20828B343d1fE37FAe469aB698E19c17F20b5", 
                                                    "0x1a642f0E3c3aF545E7AcBD38b07251B3990914F1"
                                                ]))
                    .to.be.revertedWith("ChrL2: invalid app node")
        })

        it("Invalid app node: not enough signature", async () => {
            const [anyone] = await ethers.getSigners()
            const chrL2Instance = new ChrL2__factory(chrL2Interface, anyone).attach(chrL2Address)
            expect(await chrL2Instance.directoryNodes(0)).to.eq(directoryNodes.address)
            let sig = await directoryNodes.signMessage(DecodeHexStringToByteArray("01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdc"));

            // Directory Nodes
            await chrL2Instance.updateDirectoryNodes("0x01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdc", 
                                                    [sig], 
                                                    ["0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3"]);

            // App Nodes
            await expect(chrL2Instance.updateAppNodes("0x7fcc39140a3e49a5950393cbe3d7e063adb8ede85106a1a7f3e3e610585d1c5a", 
                                                [], 
                                                [
                                                    "0x659e4a3726275edFD125F52338ECe0d54d15BD99", 
                                                    "0x75e20828B343d1fE37FAe469aB698E19c17F20b5", 
                                                    "0x1a642f0E3c3aF545E7AcBD38b07251B3990914F1"
                                                ]))
                    .to.be.revertedWith("ChrL2: not enough require signature")
        })

        it("Update directory & app node(s) successfully", async () => {
            const [anyone, other] = await ethers.getSigners()
            const chrL2Instance = new ChrL2__factory(chrL2Interface, anyone).attach(chrL2Address)
            expect(await chrL2Instance.directoryNodes(0)).to.eq(directoryNodes.address)
            let hash = keccak256(DecodeHexStringToByteArray(other.address.substring(2, other.address.length)))
            let sig = await directoryNodes.signMessage(DecodeHexStringToByteArray(hash.substring(2, hash.length)))

            // Directory Nodes
            await chrL2Instance.updateDirectoryNodes(hash, [sig], [other.address])
            expect(await chrL2Instance.directoryNodes(0)).to.eq(other.address)

            // App Nodes
            sig = await other.signMessage(DecodeHexStringToByteArray("7fcc39140a3e49a5950393cbe3d7e063adb8ede85106a1a7f3e3e610585d1c5a"))
            await chrL2Instance.updateAppNodes("0x7fcc39140a3e49a5950393cbe3d7e063adb8ede85106a1a7f3e3e610585d1c5a", [sig], 
                                                [
                                                    "0x659e4a3726275edFD125F52338ECe0d54d15BD99", 
                                                    "0x75e20828B343d1fE37FAe469aB698E19c17F20b5", 
                                                    "0x1a642f0E3c3aF545E7AcBD38b07251B3990914F1"
                                                ])
            expect(await chrL2Instance.appNodes(0)).to.eq("0x659e4a3726275edFD125F52338ECe0d54d15BD99")
            expect(await chrL2Instance.appNodes(1)).to.eq("0x75e20828B343d1fE37FAe469aB698E19c17F20b5")
            expect(await chrL2Instance.appNodes(2)).to.eq("0x1a642f0E3c3aF545E7AcBD38b07251B3990914F1")
        })
    })

    describe("Deposit", async () => {
        it("User can deposit ERC20 token to target smartcontract", async () => {
            const [deployer, user] = await ethers.getSigners()
            const tokenInstance = new TestToken__factory(deployer).attach(tokenAddress)
            const toMint = ethers.utils.parseEther("10000")

            await tokenInstance.mint(user.address, toMint)
            expect(await tokenInstance.totalSupply()).to.eq(toMint)
            expect(await tokenInstance.balanceOf(user.address)).to.eq(toMint)

            const chrL2Instance = new ChrL2__factory(chrL2Interface, user).attach(chrL2Address)
            const toDeposit = ethers.utils.parseEther("100")
            const tokenApproveInstance = new TestToken__factory(user).attach(tokenAddress)
            await tokenApproveInstance.approve(chrL2Address, toDeposit)
            await expect(chrL2Instance.deposit(tokenAddress, toDeposit))
                    .to.emit(chrL2Instance, "Deposited")
                    .withArgs(user.address, tokenAddress, toDeposit)

            expect(await chrL2Instance._balances(tokenAddress)).to.eq(toDeposit)
            expect(await tokenInstance.balanceOf(user.address)).to.eq(toMint.sub(toDeposit))
        })
    })

    describe("Withdraw", async () => {
        it("User can request withdraw by providing properly proof data", async () => {
            const [deployer, user] = await ethers.getSigners()
            const tokenInstance = new TestToken__factory(deployer).attach(tokenAddress)
            const toMint = ethers.utils.parseEther("10000")

            await tokenInstance.mint(user.address, toMint);
            expect(await tokenInstance.totalSupply()).to.eq(toMint)

            const chrL2Instance = new ChrL2__factory(chrL2Interface, user).attach(chrL2Address)
            const toDeposit = ethers.utils.parseEther("100")
            const tokenApproveInstance = new TestToken__factory(user).attach(tokenAddress)
            await tokenApproveInstance.approve(chrL2Address, toDeposit)

            let tx: ContractTransaction = await chrL2Instance.deposit(tokenAddress, toDeposit)
            let receipt: ContractReceipt = await tx.wait()
            let logs = receipt.events?.filter((x) =>  {return x.event == 'Deposited'})
            if (logs !== undefined) {
                let log = logs[0]
                const blockNumber = hexZeroPad(intToHex(log.blockNumber), 32)
                const serialNumber = hexZeroPad(intToHex(log.blockNumber + log.logIndex), 32)
                const contractAdress = hexZeroPad(tokenAddress, 32)
                const toAddress = hexZeroPad(user.address, 32)
                const amount = log.data
                let event: string = ''
                event = event.concat(serialNumber.substring(2, serialNumber.length))
                event = event.concat(contractAdress.substring(2, contractAdress.length))
                event = event.concat(toAddress.substring(2, toAddress.length))
                event = event.concat(amount.substring(2, amount.length))

                // swap toAddress and contractAdress position to make maliciousEvent
                let maliciousEvent: string = ''
                maliciousEvent = maliciousEvent.concat(serialNumber.substring(2, serialNumber.length))
                maliciousEvent = maliciousEvent.concat(toAddress.substring(2, toAddress.length))
                maliciousEvent = maliciousEvent.concat(contractAdress.substring(2, contractAdress.length))
                maliciousEvent = maliciousEvent.concat(amount.substring(2, amount.length))

                let data = DecodeHexStringToByteArray(event)
                let maliciousData = DecodeHexStringToByteArray(maliciousEvent)
                let hashEventLeaf = keccak256(data)
                let maliciousHashEventLeaf = keccak256(keccak256(data))
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
                let extraDataMerkleRoot = "73178A330CE92CBA29E3B6A16AF70FC4BAB535253969581E3B5CC46D32FB7132"
                let extraDataMerkleRootHashedLeaf = hashGtvBytes32Leaf(DecodeHexStringToByteArray(extraDataMerkleRoot))
                
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
                let node5678 = postchainMerkleNodeHash([0x00, node56, extraDataMerkleRootHashedLeaf])

                let blockRid = postchainMerkleNodeHash([0x7, node1234, node5678])
                let maliciousBlockRid = postchainMerkleNodeHash([0x7, node1234, node1234])
                let blockHeader: BytesLike = ''
                let maliciousBlockHeader: BytesLike = ''
                blockHeader = blockHeader.concat(blockchainRid, blockRid.substring(2, blockRid.length), previousBlockRid,
                                    merkleRootHashHashedLeaf.substring(2, merkleRootHashHashedLeaf.length),
                                    node4.substring(2, node4.length), node5.substring(2, node5.length),
                                    dependenciesHashedLeaf.substring(2, dependenciesHashedLeaf.length),
                                    extraDataMerkleRootHashedLeaf.substring(2, extraDataMerkleRootHashedLeaf.length)
                )

                maliciousBlockHeader = maliciousBlockHeader.concat(blockchainRid, maliciousBlockRid.substring(2, maliciousBlockRid.length), previousBlockRid, 
                                    merkleRootHashHashedLeaf.substring(2, merkleRootHashHashedLeaf.length),
                                    node4.substring(2, node4.length), node5.substring(2, node5.length),
                                    dependenciesHashedLeaf.substring(2, dependenciesHashedLeaf.length),
                                    extraDataMerkleRootHashedLeaf.substring(2, extraDataMerkleRootHashedLeaf.length)
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
                let maliciousEventProof = {
                    leaf: DecodeHexStringToByteArray(maliciousHashEventLeaf.substring(2, maliciousHashEventLeaf.length)),
                    position: 0,
                    merkleProofs: merkleProof,
                }                
                let el2HashedLeaf = hashGtvBytes64Leaf(DecodeHexStringToByteArray(el2Leaf))
                let extraRoot = el2HashedLeaf
                let el2Proof = {
                    el2Leaf: DecodeHexStringToByteArray(el2Leaf),
                    el2HashedLeaf: DecodeHexStringToByteArray(el2HashedLeaf.substring(2, el2HashedLeaf.length)),
                    el2Position: 0,
                    extraRoot: DecodeHexStringToByteArray(extraRoot.substring(2, extraRoot.length)),
                    extraMerkleProofs: [],
                }
                let maliciousEl2Proof = {
                    el2Leaf: DecodeHexStringToByteArray(el2Leaf),
                    el2HashedLeaf: DecodeHexStringToByteArray(el2HashedLeaf.substring(2, el2HashedLeaf.length)),
                    el2Position: 0,
                    extraRoot: DecodeHexStringToByteArray(extraRoot.substring(2, extraRoot.length)),
                    extraMerkleProofs: [
                        DecodeHexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000"), 
                        DecodeHexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000")                        
                    ],
                }
                await expect(chrL2Instance.withdraw_request(maliciousData, eventProof, 
                    DecodeHexStringToByteArray(blockHeader), 
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], el2Proof)
                ).to.be.revertedWith('Postchain: invalid event')
                await expect(chrL2Instance.withdraw_request(data, eventProof, 
                    DecodeHexStringToByteArray(maliciousBlockHeader), 
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], el2Proof)
                ).to.be.revertedWith('Postchain: invalid block header')
                await expect(chrL2Instance.withdraw_request(data, eventProof, 
                    DecodeHexStringToByteArray(blockHeader), 
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], maliciousEl2Proof)
                ).to.be.revertedWith('Postchain: invalid el2 extra data')
                await expect(chrL2Instance.withdraw_request(data, maliciousEventProof, 
                    DecodeHexStringToByteArray(blockHeader),
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], el2Proof)
                ).to.be.revertedWith('ChrL2: invalid merkle proof')
                await expect(chrL2Instance.withdraw_request(data, eventProof, 
                    DecodeHexStringToByteArray(blockHeader), 
                    [], el2Proof)
                ).to.be.revertedWith('ChrL2: block signature is invalid')

                await expect(chrL2Instance.withdraw_request(data, eventProof, 
                    DecodeHexStringToByteArray(blockHeader), 
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], el2Proof)
                ).to.emit(chrL2Instance, "WithdrawRequest")
                .withArgs(user.address, tokenAddress, toDeposit)

                await expect(chrL2Instance.withdraw_request(data, eventProof, 
                    DecodeHexStringToByteArray(blockHeader), 
                    [DecodeHexStringToByteArray(sig.substring(2, sig.length))], el2Proof)
                ).to.be.revertedWith('ChrL2: event hash was already used')

                await expect(chrL2Instance.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)), 
                    deployer.address)).to.revertedWith("ChrL2: no fund for the beneficiary")

                await expect(chrL2Instance.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)), 
                    user.address)).to.revertedWith("ChrL2: not mature enough to withdraw the fund")

                // force mining 100 blocks
                for (let i = 0; i < 100; i++) {
                    await ethers.provider.send('evm_mine', [])
                }

                expect(await tokenInstance.balanceOf(user.address)).to.eq(toMint.sub(toDeposit))
                expect(await chrL2Instance._balances(tokenAddress)).to.eq(toDeposit)
                await expect(chrL2Instance.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)), 
                    deployer.address)).to.be.revertedWith('ChrL2: no fund for the beneficiary')
                await expect(chrL2Instance.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address))
                .to.emit(chrL2Instance, "Withdrawal")
                .withArgs(user.address, tokenAddress, toDeposit)
                expect(await chrL2Instance._balances(tokenAddress)).to.eq(0)
                expect(await tokenInstance.balanceOf(user.address)).to.eq(toMint)
                await expect(chrL2Instance.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address)).to.be.revertedWith('ChrL2: fund was already claimed')
            }
        })
    })
})

var DecodeHexStringToByteArray = function (hexString: string) {
    var result = [];
    while (hexString.length >= 2) { 
        result.push(parseInt(hexString.substring(0, 2), 16))
        hexString = hexString.substring(2, hexString.length)
    }
    return result;
}

var postchainMerkleNodeHash = function(values: any[]): string {
    return ethers.utils.soliditySha256(['uint8', 'bytes32', 'bytes32'], values)
}

var hashGtvBytes32Leaf = function (data: BytesLike): string {
    var result: string = ''
    result = ethers.utils.soliditySha256(['uint8', 'uint8', 'uint8', 'uint8', 'uint8', 'bytes32'], [0x1, 0xA1, 32+2, 0x4, 32, data])
    return result
}

var hashGtvBytes64Leaf = function (data: BytesLike): string {
    var result: string = ''
    result = ethers.utils.soliditySha256(['uint8', 'uint8', 'uint8', 'uint8', 'uint8', 'bytes'], [0x1, 0xA1, 64+2, 0x4, 64, data])
    return result
}