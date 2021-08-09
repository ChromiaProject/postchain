import { ethers } from "hardhat";
import chai from "chai";
import { solidity } from "ethereum-waffle";
import { TestToken__factory, ChrL2__factory } from "../typechain";

chai.use(solidity);
const { expect } = chai;

describe("ChrL2", () => {
    let tokenAddress: string;
    let chrL2Address: string;

    beforeEach(async () => {
        const [deployer] = await ethers.getSigners();
        const tokenFactory = new TestToken__factory(deployer);
        const tokenContract = await tokenFactory.deploy();
        tokenAddress = tokenContract.address;
        expect(await tokenContract.totalSupply()).to.eq(0);

        const chrl2Factory = new ChrL2__factory(deployer);
        const chrL2Contract = await chrl2Factory.deploy();
        chrL2Address = chrL2Contract.address;
    });

    describe("Utility", async () => {
        describe("SHA3", async () => {

            it("Non-empty node sha3 hash function", async () => {
                const [everyone] = await ethers.getSigners();
                const chrL2Instance = new ChrL2__factory(everyone).attach(chrL2Address);                
                expect(await chrL2Instance.sha3Hash("0x24860e5aba544f2344ca0f3b285c33e7b442e2f2c6d47d4b70dddce79df17f20", 
                                                    "0x6d8f6f192029b21aedeaa1107974ea6f21c17e071e0ad1268cef4bf16e72772d"))
                                                    .to.eq("0x0cf42c3b43ad0c84c02c3e553520261b5650ece5ed65bb79a07592f586637f6a");
            })
    
            it("Right empty node sha3 hash function", async () => {
                const [everyone] = await ethers.getSigners();
                const chrL2Instance = new ChrL2__factory(everyone).attach(chrL2Address);                
                expect(await chrL2Instance.sha3Hash("0x6c5efa1707c93140989e0f95b9a0b8616e0c8ef51392617bf9c917aff96ef769", 
                                                    "0x0000000000000000000000000000000000000000000000000000000000000000"))
                                                    .to.eq("0x48febd01a647789e62260070b31361f1b12a0fe90bc7ebb700b511b12b9ca410");
            })
    
            it("Left empty node sha3 hash function", async () => {
                const [everyone] = await ethers.getSigners();
                const chrL2Instance = new ChrL2__factory(everyone).attach(chrL2Address);                
                expect(await chrL2Instance.sha3Hash("0x0000000000000000000000000000000000000000000000000000000000000000", 
                                                    "0x6c5efa1707c93140989e0f95b9a0b8616e0c8ef51392617bf9c917aff96ef769"))
                                                    .to.eq("0x48febd01a647789e62260070b31361f1b12a0fe90bc7ebb700b511b12b9ca410");
            })

            it("All empty node", async () => {
                const [everyone] = await ethers.getSigners();
                const chrL2Instance = new ChrL2__factory(everyone).attach(chrL2Address);                
                expect(await chrL2Instance.sha3Hash("0x0000000000000000000000000000000000000000000000000000000000000000", 
                                                    "0x0000000000000000000000000000000000000000000000000000000000000000"))
                                                    .to.eq("0x0000000000000000000000000000000000000000000000000000000000000000");                
            })
        })

        describe("Merkle Proof", async () => {
            it("Verify valid merkle proof properly", async () => {
                const [everyone] = await ethers.getSigners();
                const chrL2Instance = new ChrL2__factory(everyone).attach(chrL2Address);

                expect(await chrL2Instance.verifyMerkleProof(["0x57abe736cc8dcd7497b22ba39c7c2009088136d479e23cb7d1526751995832d6", 
                                                            "0xd103842c6a7267b533021131520f29734b4cd2256ea3851aa963339c9d763904"], 
                                                            "0xcb91922c1d21bea083e4c8689dcd0e8af187e672e8aa63a7af4032971318f7f3", 
                                                            0, 
                                                            "0x534a672f017938f18e96f552b0086f7e40ed416ab033ff439c89b75c85d9c638")).to.be.true;
            })

            it("Invalid merkle proof", async () => {
                const [everyone] = await ethers.getSigners();
                const chrL2Instance = new ChrL2__factory(everyone).attach(chrL2Address);

                expect(await chrL2Instance.verifyMerkleProof(["0x57abe736cc8dcd7497b22ba39c7c2009088136d479e23cb7d1526751995832d6", 
                                                            "0xd103842c6a7267b533021131520f29734b4cd2256ea3851aa963339c9d763904"], 
                                                            "0xcb91922c1d21bea083e4c8689dcd0e8af187e672e8aa63a7af4032971318f7f3", 
                                                            1, 
                                                            "0x534a672f017938f18e96f552b0086f7e40ed416ab033ff439c89b75c85d9c638")).to.be.false;
            })
        })

        describe("Event", async () => {
            const eventData = "0x000000000000000000000000E35487517B1BEE0E22DAF706A82F1D3D1FD963FD000000000000000000000000E105BA42B66D08AC7CA7FC48C583599044A6DAB30000000000000000000000000000000000000000000000000000000000000064";            
            it("Verify and encode event properly", async () => {
                const [everyone] = await ethers.getSigners();
                const chrL2Instance = new ChrL2__factory(everyone).attach(chrL2Address);
                const hash = "0xCB91922C1D21BEA083E4C8689DCD0E8AF187E672E8AA63A7AF4032971318F7F3";
                const event = await chrL2Instance.verifyEventHash(eventData, hash);

                expect(event[0]).to.eq("0xE35487517B1BEE0E22DAF706a82F1d3d1fd963FD");
                expect(event[1]).to.eq("0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3");
                expect(event[2]).to.eq(100);
            })

            it("Invalid Event", async () => {
                const [everyone] = await ethers.getSigners();
                const chrL2Instance = new ChrL2__factory(everyone).attach(chrL2Address);
                const hash = "0xCB91922C1D21BEA083E4C8689DCD0E8AF187E672E8AA63A7AF4032971318F7F2";

                await expect(chrL2Instance.verifyEventHash(eventData, hash)).to.be.revertedWith("invalid event");
            })
        })

        describe("Block Header", async () => {
            const blockHeaderData = "0x577e8805fda625bbb8c0fdae5debe407c9b0d1ba342bb0cbcf88bf25b05bcd4c9ea417e3fda249c7dc8d617ccee67726bc37b33db92c62fa3bda367085a47e36cded046c43f0bed220f053cc3e3976b0b026d098e34eff01b3b6332a654fdd705d1908a361d4359c2dafdbb4f134beb7b1bf77fa0ce45d965e249c92d113ab340000000000000000000000000000000000000000000000000000017aeb4872e2000000000000000000000000000000000000000000000000000000000000005e56bfbee83edd2c9a79ff421c95fc8ec0fa0d67258dca697e47aae56f6fbc8af3534a672f017938f18e96f552b0086f7e40ed416ab033ff439c89b75c85d9c638c3a853d3a9da54943a273f348a1e8ad645fa7e8f7a148d04aa56893f6795ae4d";
            const invalidBlockHeaderData = "0x00000000577e8805fda625bbb8c0fdae5debe407c9b0d1ba342bb0cbcf88bf25b05bcd4c9ea417e3fda249c7dc8d617ccee67726bc37b33db92c62fa3bda367085a47e36cded046c43f0bed220f053cc3e3976b0b026d098e34eff01b3b6332a654fdd705d1908a361d4359c2dafdbb4f134beb7b1bf77fa0ce45d965e249c92d113ab340000000000000000000000000000000000000000000000000000017aeb4872e2000000000000000000000000000000000000000000000000000000000000005e56bfbee83edd2c9a79ff421c95fc8ec0fa0d67258dca697e47aae56f6fbc8af3534a672f017938f18e96f552b0086f7e40ed416ab033ff439c89b75c85d9c638c3a853d3a9da54943a273f348a1e8ad645fa7e8f7a148d04aa56893f";

            it("Verify and encode block header properly", async () => {
                const [everyone] = await ethers.getSigners();
                const chrL2Instance = new ChrL2__factory(everyone).attach(chrL2Address);
                
                const header = await chrL2Instance.verifyBlockHeader(blockHeaderData);
                expect(header[0]).to.eq("0x9ea417e3fda249c7dc8d617ccee67726bc37b33db92c62fa3bda367085a47e36");
                expect(header[1]).to.eq("0x534a672f017938f18e96f552b0086f7e40ed416ab033ff439c89b75c85d9c638");
                expect(header[2]).to.eq("0xc3a853d3a9da54943a273f348a1e8ad645fa7e8f7a148d04aa56893f6795ae4d");
            })

            it("Invalid block header", async () => {
                const [everyone] = await ethers.getSigners();
                const chrL2Instance = new ChrL2__factory(everyone).attach(chrL2Address);
                
                await expect(chrL2Instance.verifyBlockHeader(invalidBlockHeaderData)).to.be.revertedWith("invalid block header");
            })
        })
    })
    
    describe("Deposit", async () => {
        it("User can deposit ERC20 token to target smartcontract", async () => {
            const [deployer, user] = await ethers.getSigners();
            const tokenInstance = new TestToken__factory(deployer).attach(tokenAddress);
            const toMint = ethers.utils.parseEther("10000");

            await tokenInstance.mint(user.address, toMint);
            expect(await tokenInstance.totalSupply()).to.eq(toMint);

            const chrL2Instance = new ChrL2__factory(user).attach(chrL2Address);
            const toDeposit = ethers.utils.parseEther("100");
            const tokenApproveInstance = new TestToken__factory(user).attach(tokenAddress)
            await tokenApproveInstance.approve(chrL2Address, toDeposit);
            await expect(chrL2Instance.deposit(tokenAddress, toDeposit))
                    .to.emit(chrL2Instance, "Deposited")
                    .withArgs(user.address, tokenAddress, toDeposit);

            expect(await chrL2Instance._balances(user.address, tokenAddress)).to.eq(toDeposit);
        })
    })

    describe("Nodes", async () => {
        it("Invalid directory node: invalid input data", async () => {
            const [anyone] = await ethers.getSigners();
            const chrL2Instance = new ChrL2__factory(anyone).attach(chrL2Address);

            // Directory Nodes
            await expect(chrL2Instance.updateDirectoryNodes("0x01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdd", 
                                                    ["0x8d3c78fb047f38e1b9a1b74f0695f0e494e5924b239c94597e3bbf9ec405bfb35ee015d470c7259be0bcfa559b6e83202f1f8dee01c01d96d2566a11a83d0f771c"], 
                                                    ["0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3"]))
                    .to.be.revertedWith("ChrL2: Invalid directory node");
        })

        it("Invalid directory node: not enough signature", async () => {
            const [anyone] = await ethers.getSigners();
            const chrL2Instance = new ChrL2__factory(anyone).attach(chrL2Address);

            // Directory Nodes
            await chrL2Instance.updateDirectoryNodes("0x01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdc", 
                                                    ["0x8d3c78fb047f38e1b9a1b74f0695f0e494e5924b239c94597e3bbf9ec405bfb35ee015d470c7259be0bcfa559b6e83202f1f8dee01c01d96d2566a11a83d0f771c"], 
                                                    ["0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3"]);

            await expect(chrL2Instance.updateDirectoryNodes("0x01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdc", 
                                                    [],
                                                    ["0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3"]))
                    .to.be.revertedWith("ChrL2: Not enough require signature");
        })

        it("Invalid app node: invalid input data", async () => {
            const [anyone] = await ethers.getSigners();
            const chrL2Instance = new ChrL2__factory(anyone).attach(chrL2Address);

            // App Nodes
            await expect(chrL2Instance.updateAppNodes("0x7fcc39140a3e49a5950393cbe3d7e063adb8ede85106a1a7f3e3e610585d1c5b", 
                                                ["0xf9d0a80283aa2a1088adafd4e99453be610a9fd61c69d40b36deab48c84b54d47895765b82d9ae442a00671d462d5a83b34e8bc5fb9c64a9e453f8f18be68f381b"], 
                                                [
                                                    "0x659e4a3726275edFD125F52338ECe0d54d15BD99", 
                                                    "0x75e20828B343d1fE37FAe469aB698E19c17F20b5", 
                                                    "0x1a642f0E3c3aF545E7AcBD38b07251B3990914F1"
                                                ]))
                    .to.be.revertedWith("ChrL2: Invalid app node");
        })

        it("Invalid app node: not enough signature", async () => {
            const [anyone] = await ethers.getSigners();
            const chrL2Instance = new ChrL2__factory(anyone).attach(chrL2Address);

            // Directory Nodes
            await chrL2Instance.updateDirectoryNodes("0x01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdc", 
                                                    ["0x8d3c78fb047f38e1b9a1b74f0695f0e494e5924b239c94597e3bbf9ec405bfb35ee015d470c7259be0bcfa559b6e83202f1f8dee01c01d96d2566a11a83d0f771c"], 
                                                    ["0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3"]);

            // App Nodes
            await expect(chrL2Instance.updateAppNodes("0x7fcc39140a3e49a5950393cbe3d7e063adb8ede85106a1a7f3e3e610585d1c5a", 
                                                [], 
                                                [
                                                    "0x659e4a3726275edFD125F52338ECe0d54d15BD99", 
                                                    "0x75e20828B343d1fE37FAe469aB698E19c17F20b5", 
                                                    "0x1a642f0E3c3aF545E7AcBD38b07251B3990914F1"
                                                ]))
                    .to.be.revertedWith("ChrL2: Not enough require signature");
        })

        it("Update directory & app node(s) successfully", async () => {
            const [anyone] = await ethers.getSigners();
            const chrL2Instance = new ChrL2__factory(anyone).attach(chrL2Address);

            // Directory Nodes
            await chrL2Instance.updateDirectoryNodes("0x01a775f1ca2c6ab3e5f37f3e8541fcc8cbc64a5a0414e6be1e724677142a7fdc", 
                                                    ["0x8d3c78fb047f38e1b9a1b74f0695f0e494e5924b239c94597e3bbf9ec405bfb35ee015d470c7259be0bcfa559b6e83202f1f8dee01c01d96d2566a11a83d0f771c"], 
                                                    ["0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3"]);
            expect(await chrL2Instance.directoryNodes(0)).to.eq("0xe105Ba42B66D08Ac7Ca7FC48c583599044a6DAb3");

            // App Nodes
            await chrL2Instance.updateAppNodes("0x7fcc39140a3e49a5950393cbe3d7e063adb8ede85106a1a7f3e3e610585d1c5a", 
                                                ["0xf9d0a80283aa2a1088adafd4e99453be610a9fd61c69d40b36deab48c84b54d47895765b82d9ae442a00671d462d5a83b34e8bc5fb9c64a9e453f8f18be68f381b"], 
                                                [
                                                    "0x659e4a3726275edFD125F52338ECe0d54d15BD99", 
                                                    "0x75e20828B343d1fE37FAe469aB698E19c17F20b5", 
                                                    "0x1a642f0E3c3aF545E7AcBD38b07251B3990914F1"
                                                ]);
            expect(await chrL2Instance.appNodes(0)).to.eq("0x659e4a3726275edFD125F52338ECe0d54d15BD99");
            expect(await chrL2Instance.appNodes(1)).to.eq("0x75e20828B343d1fE37FAe469aB698E19c17F20b5");
            expect(await chrL2Instance.appNodes(2)).to.eq("0x1a642f0E3c3aF545E7AcBD38b07251B3990914F1");
        })
    })
})