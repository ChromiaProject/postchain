import { ethers, upgrades } from "hardhat";
import chai from "chai";
import { solidity } from "ethereum-waffle";
import { TestToken__factory, TokenBridge__factory, TestDelegator__factory, TestDelegator } from "../src/types";
import { SignerWithAddress } from "@nomiclabs/hardhat-ethers/signers";
import { BytesLike, hexZeroPad, keccak256, solidityPack} from "ethers/lib/utils";
import { ContractReceipt, ContractTransaction } from "ethers";
import { intToHex } from "ethjs-util";
import { DecodeHexStringToByteArray, hashGtvBytes32Leaf, hashGtvBytes64Leaf, hashGtvIntegerLeaf, postchainMerkleNodeHash} from "./utils"

chai.use(solidity);
const { expect } = chai;

describe("Token Bridge Test", () => {
    let tokenAddress: string;
    let bridgeAddress: string;
    let testDelegatorAddress: string;
    let admin: SignerWithAddress;
    let validator1: SignerWithAddress;
    let validator2: SignerWithAddress;

    beforeEach(async () => {
        const [deployer] = await ethers.getSigners()
        ;[admin, validator1, validator2] = await ethers.getSigners()
        const tokenFactory = new TestToken__factory(deployer)
        const tokenContract = await tokenFactory.deploy()
        tokenAddress = tokenContract.address
        expect(await tokenContract.totalSupply()).to.eq(0)

        const testDelegatorFactory = new TestDelegator__factory(deployer);
        const testDelegator = await testDelegatorFactory.deploy()
        testDelegatorAddress = testDelegator.address;

        const bridgeFactory = new TokenBridge__factory(admin)
        const bridge = await upgrades.deployProxy(bridgeFactory, [[validator1.address, validator2.address]])
        bridgeAddress = bridge.address
    });

    describe("Utility", async () => {
        describe("hash", async () => {
            var testDelegatorInstance: TestDelegator
            beforeEach(async () => {            
                const [everyone] = await ethers.getSigners()
                testDelegatorInstance = new TestDelegator__factory(everyone).attach(testDelegatorAddress)
            })

            it("Non-empty node sha3 hash function", async () => {
                expect(await testDelegatorInstance.hash("0x24860e5aba544f2344ca0f3b285c33e7b442e2f2c6d47d4b70dddce79df17f20",
                                                "0x6d8f6f192029b21aedeaa1107974ea6f21c17e071e0ad1268cef4bf16e72772d"))
                                                    .to.eq("0x0cf42c3b43ad0c84c02c3e553520261b5650ece5ed65bb79a07592f586637f6a");
            })
    
            it("Right empty node sha3 hash function", async () => {
                expect(await testDelegatorInstance.hash("0x6c5efa1707c93140989e0f95b9a0b8616e0c8ef51392617bf9c917aff96ef769",
                                                    "0x0000000000000000000000000000000000000000000000000000000000000000"))
                                                    .to.eq("0x48febd01a647789e62260070b31361f1b12a0fe90bc7ebb700b511b12b9ca410");
            })
    
            it("Left empty node sha3 hash function", async () => {
                expect(await testDelegatorInstance.hash("0x0000000000000000000000000000000000000000000000000000000000000000",
                                                    "0x6c5efa1707c93140989e0f95b9a0b8616e0c8ef51392617bf9c917aff96ef769"))
                                                    .to.eq("0x48febd01a647789e62260070b31361f1b12a0fe90bc7ebb700b511b12b9ca410");
            })

            it("All empty node", async () => {
                expect(await testDelegatorInstance.hash("0x0000000000000000000000000000000000000000000000000000000000000000",
                                                    "0x0000000000000000000000000000000000000000000000000000000000000000"))
                                                    .to.eq("0x0000000000000000000000000000000000000000000000000000000000000000");                
            })

            it("hash gtv integer leaf 0", async () => {
                expect(await testDelegatorInstance.hashGtvIntegerLeaf(0)).to.eq("0x90B136DFC51E08EE70ED929C620C0808D4230EC1015D46C92CCAA30772651DC0".toLowerCase());
            })

            it("hash gtv integer leaf 1", async () => {
                expect(await testDelegatorInstance.hashGtvIntegerLeaf(1)).to.eq("0x6CCD14B5A877874DDC7CA52BD3AEDED5543B73A354779224BBB86B0FD315B418".toLowerCase());
            })

            it("hash gtv integer leaf 127", async () => {
                expect(await testDelegatorInstance.hashGtvIntegerLeaf(127)).to.eq("0xEBA1A4FE3CDC6C5089D6222F00980599D5E943A933AD11BDEC942B08D1C8D419".toLowerCase());
            })

            it("hash gtv integer leaf 128", async () => {
                expect(await testDelegatorInstance.hashGtvIntegerLeaf(128)).to.eq("0xCCC9C7E4A8FC166199E7708146EC6D043DCAD0A20266E064E802E5DD724A66DA".toLowerCase());
            })

            it("hash gtv integer leaf 168", async () => {
                expect(await testDelegatorInstance.hashGtvIntegerLeaf(168)).to.eq("0x1DD1D428D59F66807F753FB3E307A65B1B57EACE358A4A94745AA049593A5AEE".toLowerCase());
            })

            it("hash gtv integer leaf 255", async () => {
                expect(await testDelegatorInstance.hashGtvIntegerLeaf(255)).to.eq("0x7698DE397F332E1BCC03967CCC1196B0DACB86DC3700FC19566C4F3C322D599E".toLowerCase());
            })

            it("hash gtv integer leaf 256", async () => {
                expect(await testDelegatorInstance.hashGtvIntegerLeaf(256)).to.eq("0xCA5F98D59E2E5FE04936A6CCF67F6BF8B5ABDF925BD0FE647A8718CBCE94BD9A".toLowerCase());
            })

            it("hash gtv integer leaf 1256", async () => {
                expect(await testDelegatorInstance.hashGtvIntegerLeaf(1256)).to.eq("0x0A336A98550BBC8182BE8DBA0517E0A6D0E49E2A598468A4B6FBF3AD53AC7BEA".toLowerCase());
            })

            it("hash gtv integer leaf 1234567890", async () => {
                expect(await testDelegatorInstance.hashGtvIntegerLeaf(1234567890)).to.eq("0x91F23A381089997DF175AF0AE0DD3E44B651C255ABECA1683F15D831B59C236E".toLowerCase());
            })            
        })

        describe("Merkle Proof", async () => {
            it("Verify valid merkle proof properly", async () => {
                const [everyone] = await ethers.getSigners()
                const testDelegatorInstance = new TestDelegator__factory(everyone).attach(testDelegatorAddress)

                expect(await testDelegatorInstance.verify(["0x57abe736cc8dcd7497b22ba39c7c2009088136d479e23cb7d1526751995832d6",
                                                            "0xd103842c6a7267b533021131520f29734b4cd2256ea3851aa963339c9d763904"], 
                                                            "0xcb91922c1d21bea083e4c8689dcd0e8af187e672e8aa63a7af4032971318f7f3", 
                                                            0, 
                                                            "0x534a672f017938f18e96f552b0086f7e40ed416ab033ff439c89b75c85d9c638")).to.be.true;
            })

            it("Invalid merkle proof", async () => {
                const [everyone] = await ethers.getSigners()
                const testDelegatorInstance = new TestDelegator__factory(everyone).attach(testDelegatorAddress)

                expect(await testDelegatorInstance.verify(["0x57abe736cc8dcd7497b22ba39c7c2009088136d479e23cb7d1526751995832d6",
                                                            "0xd103842c6a7267b533021131520f29734b4cd2256ea3851aa963339c9d763904"], 
                                                            "0xcb91922c1d21bea083e4c8689dcd0e8af187e672e8aa63a7af4032971318f7f3", 
                                                            1, 
                                                            "0x534a672f017938f18e96f552b0086f7e40ed416ab033ff439c89b75c85d9c638")).to.be.false;
            })
        })

        describe("SHA256 Merkle Proof", async () => {
            it("Verify valid SHA256 merkle proof properly", async () => {
                const [everyone] = await ethers.getSigners()
                const testDelegatorInstance = new TestDelegator__factory(everyone).attach(testDelegatorAddress)

                expect(await testDelegatorInstance.verifySHA256(["0xC7CBFEFDF46A4F2F925389E660604B7E68246802F25581C1493F2673EA2F71F1"],
                                                        "0x480DE19560D2D0DE62AD9306F1156B08CD543626AC1F28134E32C6A2FECB357A",
                                                        1,
                                                        "0x120FF48AA20416DF00C6EBC29260BD1B07588536E7BB1D835BDFECD4D7E51F78")).to.be.true;

                expect(await testDelegatorInstance.verifySHA256([
                                                            "0xC7CBFEFDF46A4F2F925389E660604B7E68246802F25581C1493F2673EA2F71F1",
                                                            "0x501D248FE65EBBF15B771F8C8CDC574942F9A07EE0C1A43D4459BB70E3088A10",
                                                            "0x1F2E3EC0A1D920108BF193452F9176BAF9F018B1FB3CFD308DC45DA12D323A07",
                                                            "0x204D463AADD2DB5530FA5F673B863FBB2D4A84B70A32EC7069A4C0114ABD7A3A"
                                                        ], 
                                                        "0x480DE19560D2D0DE62AD9306F1156B08CD543626AC1F28134E32C6A2FECB357A",
                                                        5,
                                                        "0xA89A933C4C741C222DA106103E59ADA7F45281592708F31207821A89C5E7CE40")).to.be.true;
            })

            it("Invalid SHA256 merkle proof due to incorrect merkle root", async () => {
                const [everyone] = await ethers.getSigners()
                const testDelegatorInstance = new TestDelegator__factory(everyone).attach(testDelegatorAddress)

                expect(await testDelegatorInstance.verifySHA256(["0xC7CBFEFDF46A4F2F925389E660604B7E68246802F25581C1493F2673EA2F71F1"],
                                                        "0x480DE19560D2D0DE62AD9306F1156B08CD543626AC1F28134E32C6A2FECB357A",
                                                        1,
                                                        "0x120FF48AA20416DF00C6EBC29260BD1B07588536E7BB1D835BDFECD4D7E51F79")).to.be.false;

                expect(await testDelegatorInstance.verifySHA256([
                                                            "0xC7CBFEFDF46A4F2F925389E660604B7E68246802F25581C1493F2673EA2F71F1",
                                                            "0x501D248FE65EBBF15B771F8C8CDC574942F9A07EE0C1A43D4459BB70E3088A10",
                                                            "0x1F2E3EC0A1D920108BF193452F9176BAF9F018B1FB3CFD308DC45DA12D323A07",
                                                            "0x204D463AADD2DB5530FA5F673B863FBB2D4A84B70A32EC7069A4C0114ABD7A3A"
                                                        ],
                                                        "0x480DE19560D2D0DE62AD9306F1156B08CD543626AC1F28134E32C6A2FECB357A",
                                                        5,
                                                        "0xA89A933C4C741C222DA106103E59ADA7F45281592708F31207821A89C5E7CE41")).to.be.false;
            })
        })        
    })

    describe("Nodes", async () => {
        it("Update app node(s) successfully", async () => {
            const [node1, node2, node3, other] = await ethers.getSigners()
            const bridge = new TokenBridge__factory(admin).attach(bridgeAddress)
            const otherbridge = new TokenBridge__factory(other).attach(bridgeAddress)
            await expect(otherbridge.addValidator(0, node1.address)).to.be.revertedWith("Ownable: caller is not the owner")
            // Update App Nodes
            await bridge.removeValidator(0, validator1.address)
            await bridge.removeValidator(0, validator2.address)
            await bridge.addValidator(0, node1.address)
            await bridge.addValidator(0, node2.address)
            await bridge.addValidator(0, node3.address)
            expect(await bridge.validators(0, 0)).to.eq(node1.address)
            expect(await bridge.validators(0, 1)).to.eq(node2.address)
            expect(await bridge.validators(0, 2)).to.eq(node3.address)
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

            const bridge = new TokenBridge__factory(user).attach(bridgeAddress)
            const toDeposit = ethers.utils.parseEther("100")
            const tokenApproveInstance = new TestToken__factory(user).attach(tokenAddress)
            const name = await tokenApproveInstance.name()
            const symbol = await tokenApproveInstance.symbol()
            const expectedPayload = ''.concat(
                "0xa5", "84", "000000a5", "30", "84", "0000009f", // Gtv tag, Ber length, Length, Ber tag, Ber length, Value length
                "a1", "16", "04", "14", // Gtv tag, Length, Ber tag, Value length
                user.address.substring(2),
                "a1", "16", "04", "14", // Gtv tag, Length, Ber tag, Value Length
                tokenAddress.substring(2),
                "a6", "23", "02", "21", "00", // Gtv tag, Length, Ber tag, Value Length, Zero padding for signed bit
                hexZeroPad(toDeposit.toHexString(), 32).substring(2),
                "a2", "84", "00000010", "0c", "84", "0000000a",
                solidityPack(["string"], [name]).substring(2),
                "a2", "84", "00000009", "0c", "84", "00000003",
                solidityPack(["string"], [symbol]).substring(2),
                "a6", "23", "02", "21", "00", // Gtv tag, Length, Ber tag, Value Length, Zero padding for signed bit
                hexZeroPad("0x12", 32).substring(2), // Default decimals is 18
            )
            await tokenApproveInstance.approve(bridgeAddress, toDeposit)
            await expect(bridge.deposit(tokenAddress, toDeposit))
                    .to.emit(bridge, "Deposited")
                    .withArgs(0, expectedPayload.toLowerCase())

            expect(await bridge._balances(tokenAddress)).to.eq(toDeposit)
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

            const bridge = new TokenBridge__factory(user).attach(bridgeAddress)
            const toDeposit = ethers.utils.parseEther("100")
            const tokenApproveInstance = new TestToken__factory(user).attach(tokenAddress)
            await tokenApproveInstance.approve(bridgeAddress, toDeposit)

            let tx: ContractTransaction = await bridge.deposit(tokenAddress, toDeposit)
            let receipt: ContractReceipt = await tx.wait()
            let logs = receipt.events?.filter((x) =>  {return x.event == 'Deposited'})
            if (logs !== undefined) {
                let log = logs[0]
                const blockNumber = hexZeroPad(intToHex(log.blockNumber), 32)
                const serialNumber = hexZeroPad(intToHex(log.blockNumber + log.logIndex), 32)
                const contractAddress = hexZeroPad(tokenAddress, 32)
                const toAddress = hexZeroPad(user.address, 32)
                const amountHex = hexZeroPad(toDeposit.toHexString(), 32)
                let event: string = ''
                event = event.concat(serialNumber.substring(2, serialNumber.length))
                event = event.concat(contractAddress.substring(2, contractAddress.length))
                event = event.concat(toAddress.substring(2, toAddress.length))
                event = event.concat(amountHex.substring(2, amountHex.length))

                // swap toAddress and contractAddress position to make maliciousEvent
                let maliciousEvent: string = ''
                maliciousEvent = maliciousEvent.concat(serialNumber.substring(2, serialNumber.length))
                maliciousEvent = maliciousEvent.concat(toAddress.substring(2, toAddress.length))
                maliciousEvent = maliciousEvent.concat(contractAddress.substring(2, contractAddress.length))
                maliciousEvent = maliciousEvent.concat(amountHex.substring(2, amountHex.length))

                let data = DecodeHexStringToByteArray(event)
                let maliciousData = DecodeHexStringToByteArray(maliciousEvent)
                let hashEventLeaf = keccak256(data)
                let maliciousHashEventLeaf = keccak256(keccak256(data))
                let hashRootEvent = keccak256(keccak256(hashEventLeaf))
                let state = blockNumber.substring(2, blockNumber.length).concat(event)
                let hashRootState = keccak256(DecodeHexStringToByteArray(state))
                let eifLeaf = hashRootEvent.substring(2, hashRootEvent.length).concat(hashRootState.substring(2, hashRootState.length))

                let blockchainRid = "977dd435e17d637c2c71ebb4dec4ff007a4523976dc689c7bcb9e6c514e4c795"
                let previousBlockRid = "49e46bf022de1515cbb2bf0f69c62c071825a9b940e8f3892acb5d2021832ba0"
                let merkleRootHash = "96defe74f43fcf2d12a1844bcd7a3a7bcb0d4fa191776953dae3f1efb508d866"
                let merkleRootHashHashedLeaf = hashGtvBytes32Leaf(DecodeHexStringToByteArray(merkleRootHash))
                let dependencies = "56bfbee83edd2c9a79ff421c95fc8ec0fa0d67258dca697e47aae56f6fbc8af3"
                let dependenciesHashedLeaf = hashGtvBytes32Leaf(DecodeHexStringToByteArray(dependencies))

                // This merkle root is calculated in the postchain code
                let extraDataMerkleRoot = "FCB5E0B2223B4EADD2674EDFDD9B6042F440F533E33A3DDAE1A3EDEF869597C6"

                let node1 = hashGtvBytes32Leaf(DecodeHexStringToByteArray(blockchainRid))
                let node2 = hashGtvBytes32Leaf(DecodeHexStringToByteArray(previousBlockRid))
                let node12 = postchainMerkleNodeHash([0x00, node1, node2])
                let node3 = hashGtvBytes32Leaf(DecodeHexStringToByteArray(merkleRootHash))
                let timestamp = 1629878444220
                let height = 46
                let node4 = hashGtvIntegerLeaf(timestamp)
                let node34 = postchainMerkleNodeHash([0x00, node3, node4])
                let node5 = hashGtvIntegerLeaf(height)
                let node6 = hashGtvBytes32Leaf(DecodeHexStringToByteArray(dependencies))
                let node56 = postchainMerkleNodeHash([0x00, node5, node6])
                let node1234 = postchainMerkleNodeHash([0x00, node12, node34])
                let node5678 = postchainMerkleNodeHash([0x00, node56, DecodeHexStringToByteArray(extraDataMerkleRoot)])

                let blockRid = postchainMerkleNodeHash([0x7, node1234, node5678])
                let maliciousBlockRid = postchainMerkleNodeHash([0x7, node1234, node1234])
                let blockHeader: BytesLike = ''
                let maliciousBlockHeader: BytesLike = ''
                let ts = hexZeroPad(intToHex(timestamp), 32)
                let h = hexZeroPad(intToHex(height), 32)
                blockHeader = blockHeader.concat(blockchainRid, blockRid.substring(2, blockRid.length), previousBlockRid,
                                    merkleRootHashHashedLeaf.substring(2, merkleRootHashHashedLeaf.length),
                                    ts.substring(2, ts.length), h.substring(2, h.length),
                                    dependenciesHashedLeaf.substring(2, dependenciesHashedLeaf.length),
                                    extraDataMerkleRoot
                )

                maliciousBlockHeader = maliciousBlockHeader.concat(blockchainRid, maliciousBlockRid.substring(2, maliciousBlockRid.length), previousBlockRid, 
                                    merkleRootHashHashedLeaf.substring(2, merkleRootHashHashedLeaf.length),
                                    ts.substring(2, ts.length), h.substring(2, h.length),
                                    dependenciesHashedLeaf.substring(2, dependenciesHashedLeaf.length),
                                    extraDataMerkleRoot
                )

                let sig1 = await validator1.signMessage(DecodeHexStringToByteArray(blockRid.substring(2, blockRid.length)))
                let sig2 = await validator2.signMessage(DecodeHexStringToByteArray(blockRid.substring(2, blockRid.length)))
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
                let hashedLeaf = hashGtvBytes64Leaf(DecodeHexStringToByteArray(eifLeaf))
                let extraProof = {
                    leaf: DecodeHexStringToByteArray(eifLeaf),
                    hashedLeaf: DecodeHexStringToByteArray(hashedLeaf.substring(2, hashedLeaf.length)),
                    position: 1,
                    extraRoot: DecodeHexStringToByteArray(extraDataMerkleRoot),
                    extraMerkleProofs: [DecodeHexStringToByteArray("1E816A557ACB74AEBECC8B0598B81DFCDBCA912CA8BA030740F5BEAEF3FF0797")],
                }
                let invalidExtraLeaf = {
                    leaf: DecodeHexStringToByteArray(eifLeaf),
                    hashedLeaf: DecodeHexStringToByteArray(maliciousHashEventLeaf.substring(2, maliciousHashEventLeaf.length)),
                    position: 1,
                    extraRoot: DecodeHexStringToByteArray(extraDataMerkleRoot),
                    extraMerkleProofs: [DecodeHexStringToByteArray("1E816A557ACB74AEBECC8B0598B81DFCDBCA912CA8BA030740F5BEAEF3FF0797")],
                }
                let invalidExtraDataRoot = {
                    leaf: DecodeHexStringToByteArray(eifLeaf),
                    hashedLeaf: DecodeHexStringToByteArray(hashedLeaf.substring(2, hashedLeaf.length)),
                    position: 1,
                    extraRoot: DecodeHexStringToByteArray("04D17CC3DD96E88DF05A943EC79DD436F220E84BA9E5F35CACF627CA225424A2"),
                    extraMerkleProofs: [DecodeHexStringToByteArray("1E816A557ACB74AEBECC8B0598B81DFCDBCA912CA8BA030740F5BEAEF3FF0797")],
                }
                let maliciousEl2Proof = {
                    leaf: DecodeHexStringToByteArray(eifLeaf),
                    hashedLeaf: DecodeHexStringToByteArray(hashedLeaf.substring(2, hashedLeaf.length)),
                    position: 0,
                    extraRoot: DecodeHexStringToByteArray(extraDataMerkleRoot),
                    extraMerkleProofs: [
                        DecodeHexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000"), 
                        DecodeHexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000")                        
                    ],
                }
                await expect(bridge.withdrawRequest(maliciousData, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length)), 
                        DecodeHexStringToByteArray(sig2.substring(2, sig2.length))
                    ], 
                    [validator1.address, validator2.address],
                    extraProof)
                ).to.be.revertedWith('Postchain: invalid event')
                await expect(bridge.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length)), 
                        DecodeHexStringToByteArray(sig2.substring(2, sig2.length))
                    ], 
                    [validator1.address, validator2.address],
                    invalidExtraLeaf)
                ).to.be.revertedWith('Postchain: invalid EIF extra data')
                await expect(bridge.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length)), 
                        DecodeHexStringToByteArray(sig2.substring(2, sig2.length))
                    ], 
                    [validator1.address, validator2.address],
                    invalidExtraDataRoot)
                ).to.be.revertedWith('Postchain: invalid extra data root')
                await expect(bridge.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(maliciousBlockHeader),
                    [
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length)), 
                        DecodeHexStringToByteArray(sig2.substring(2, sig2.length))
                    ], 
                    [validator1.address, validator2.address],
                    extraProof)
                ).to.be.revertedWith('Postchain: invalid block header')
                await expect(bridge.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length)), 
                        DecodeHexStringToByteArray(sig2.substring(2, sig2.length))
                    ], 
                    [validator1.address, validator2.address],
                    maliciousEl2Proof)
                ).to.be.revertedWith('Postchain: invalid EIF extra merkle proof')
                await expect(bridge.withdrawRequest(data, maliciousEventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length)), 
                        DecodeHexStringToByteArray(sig2.substring(2, sig2.length))
                    ], 
                    [validator1.address, validator2.address],
                    extraProof)
                ).to.be.revertedWith('TokenBridge: invalid merkle proof')
                await expect(bridge.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [], [], extraProof)
                ).to.be.revertedWith('TokenBridge: block signature is invalid')
                await expect(bridge.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length)), 
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length))
                    ], 
                    [validator1.address, validator2.address],
                    extraProof)
                ).to.be.revertedWith('TokenBridge: duplicate signature or signers is out of order')
                await expect(bridge.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [
                        DecodeHexStringToByteArray(sig2.substring(2, sig2.length)), 
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length))
                    ], 
                    [validator2.address, validator1.address],
                    extraProof)
                ).to.be.revertedWith('TokenBridge: duplicate signature or signers is out of order')
                let sig = await admin.signMessage(DecodeHexStringToByteArray(blockRid.substring(2, blockRid.length)))
                await expect(bridge.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [
                        DecodeHexStringToByteArray(sig.substring(2, sig.length)), 
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length))
                    ], 
                    [admin.address, validator1.address],
                    extraProof)
                ).to.be.revertedWith('TokenBridge: signer is not validator')
                await expect(bridge.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length)), 
                        DecodeHexStringToByteArray(sig2.substring(2, sig2.length))
                    ], 
                    [validator1.address, validator2.address],
                    extraProof)
                ).to.emit(bridge, "WithdrawRequest")
                .withArgs(user.address, tokenAddress, toDeposit)

                await expect(bridge.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [
                        DecodeHexStringToByteArray(sig1.substring(2, sig1.length)), 
                        DecodeHexStringToByteArray(sig2.substring(2, sig2.length))
                    ], 
                    [validator1.address, validator2.address],
                    extraProof)
                ).to.be.revertedWith('TokenBridge: event hash was already used')

                await expect(bridge.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    deployer.address)).to.revertedWith("TokenBridge: no fund for the beneficiary")

                await expect(bridge.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address)).to.revertedWith("TokenBridge: not mature enough to withdraw the fund")

                // force mining 98 blocks
                for (let i = 0; i < 98; i++) {
                    await ethers.provider.send('evm_mine', [])
                }

                let hashEvent = DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length))

                // directoryNode can update withdraw request status to pending (emergency case)
                let directoryNode = new TokenBridge__factory(admin).attach(bridgeAddress)
                await directoryNode.pendingWithdraw(hashEvent)

                // then user cannot withdraw the fund
                await expect(bridge.withdraw(
                    hashEvent,
                    user.address)).to.be.revertedWith('TokenBridge: fund is pending or was already claimed')

                // directoryNode can set withdraw request status back to withdrawable
                await directoryNode.unpendingWithdraw(hashEvent)

                expect(await tokenInstance.balanceOf(user.address)).to.eq(toMint.sub(toDeposit))
                expect(await bridge._balances(tokenAddress)).to.eq(toDeposit)
                await expect(bridge.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    deployer.address)).to.be.revertedWith('TokenBridge: no fund for the beneficiary')

                // now user can withdraw the fund
                await expect(bridge.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address))
                .to.emit(bridge, "Withdrawal")
                .withArgs(user.address, tokenAddress, toDeposit)
                expect(await bridge._balances(tokenAddress)).to.eq(0)
                expect(await tokenInstance.balanceOf(user.address)).to.eq(toMint)
                await expect(bridge.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address)).to.be.revertedWith('TokenBridge: fund is pending or was already claimed')
            }
        })
    })
})