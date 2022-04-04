import { ethers, upgrades } from "hardhat";
import chai from "chai";
import { solidity } from "ethereum-waffle";
import { TestToken__factory, ChrL2__factory, TestDelegator__factory } from "../src/types";
import { SignerWithAddress } from "@nomiclabs/hardhat-ethers/signers";
import { BytesLike, hexZeroPad, keccak256 } from "ethers/lib/utils";
import { BigNumber, ContractReceipt, ContractTransaction } from "ethers";
import { intToHex } from "ethjs-util";
import { DecodeHexStringToByteArray, hashGtvBytes32Leaf, hashGtvBytes64Leaf, postchainMerkleNodeHash} from "./utils"

chai.use(solidity);
const { expect } = chai;

describe("Multi-Sig", () => {
    let tokenAddress: string;
    let chrL2Address: string;
    let testDelegatorAddress: string;
    let directoryNode1: SignerWithAddress;
    let directoryNode2: SignerWithAddress;
    let directoryNode3: SignerWithAddress;
    let appNode1: SignerWithAddress;
    let appNode2: SignerWithAddress;

    beforeEach(async () => {
        const [deployer] = await ethers.getSigners()
        ;[directoryNode1, directoryNode2, directoryNode3, appNode1, appNode2] = await ethers.getSigners()
        const tokenFactory = new TestToken__factory(deployer)
        const tokenContract = await tokenFactory.deploy()
        tokenAddress = tokenContract.address
        expect(await tokenContract.totalSupply()).to.eq(0)
        const testDelegatorFactory = new TestDelegator__factory(deployer);
        const testDelegator = await testDelegatorFactory.deploy()
        testDelegatorAddress = testDelegator.address;

        const chrl2Factory = new ChrL2__factory(deployer)
        const chrl2Instance = await upgrades.deployProxy(chrl2Factory, [[directoryNode1.address, directoryNode2.address, directoryNode3.address], [appNode1.address, appNode2.address]])
        chrL2Address = chrl2Instance.address
    });

    describe("Withdraw", async () => {
        it("User can request withdraw by providing properly proof data", async () => {
            const [deployer, user] = await ethers.getSigners()
            const tokenInstance = new TestToken__factory(deployer).attach(tokenAddress)
            const toMint = ethers.utils.parseEther("10000")

            await tokenInstance.mint(user.address, toMint);
            expect(await tokenInstance.totalSupply()).to.eq(toMint)

            const chrL2Instance = new ChrL2__factory(user).attach(chrL2Address)
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
                let el2Leaf = hashRootEvent.substring(2, hashRootEvent.length).concat(hashRootState.substring(2, hashRootState.length))

                let blockchainRid = "977dd435e17d637c2c71ebb4dec4ff007a4523976dc689c7bcb9e6c514e4c795"
                let previousBlockRid = "49e46bf022de1515cbb2bf0f69c62c071825a9b940e8f3892acb5d2021832ba0"
                let merkleRootHash = "96defe74f43fcf2d12a1844bcd7a3a7bcb0d4fa191776953dae3f1efb508d866"
                let merkleRootHashHashedLeaf = hashGtvBytes32Leaf(DecodeHexStringToByteArray(merkleRootHash))
                let dependencies = "56bfbee83edd2c9a79ff421c95fc8ec0fa0d67258dca697e47aae56f6fbc8af3"
                let dependenciesHashedLeaf = hashGtvBytes32Leaf(DecodeHexStringToByteArray(dependencies))

                // This merkle root is calculated in the postchain code
                let extraDataMerkleRoot = "05608E8DC1763FC003015CE81318E60C260EF71804770252D7E487CA33ABB2C2"

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
                let maliciousBlockRid = postchainMerkleNodeHash([0x7, node1234, node1234])
                let blockHeader: BytesLike = ''
                let maliciousBlockHeader: BytesLike = ''
                blockHeader = blockHeader.concat(blockchainRid, blockRid.substring(2, blockRid.length), previousBlockRid,
                                    merkleRootHashHashedLeaf.substring(2, merkleRootHashHashedLeaf.length),
                                    node4.substring(2, node4.length), node5.substring(2, node5.length),
                                    dependenciesHashedLeaf.substring(2, dependenciesHashedLeaf.length),
                                    extraDataMerkleRoot
                )

                maliciousBlockHeader = maliciousBlockHeader.concat(blockchainRid, maliciousBlockRid.substring(2, maliciousBlockRid.length), previousBlockRid, 
                                    merkleRootHashHashedLeaf.substring(2, merkleRootHashHashedLeaf.length),
                                    node4.substring(2, node4.length), node5.substring(2, node5.length),
                                    dependenciesHashedLeaf.substring(2, dependenciesHashedLeaf.length),
                                    extraDataMerkleRoot
                )

                let sig1 = await appNode1.signMessage(DecodeHexStringToByteArray(blockRid.substring(2, blockRid.length)))
                let sig2 = await appNode2.signMessage(DecodeHexStringToByteArray(blockRid.substring(2, blockRid.length)))
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
                let el2Proof = {
                    el2Leaf: DecodeHexStringToByteArray(el2Leaf),
                    el2HashedLeaf: DecodeHexStringToByteArray(el2HashedLeaf.substring(2, el2HashedLeaf.length)),
                    el2Position: 1,
                    extraRoot: DecodeHexStringToByteArray(extraDataMerkleRoot),
                    extraMerkleProofs: [DecodeHexStringToByteArray("36F5BC29C2E9593F50B0E017700DC775F7F899FEA2FE8CEE8EEA5DDBCD483F0C")],
                }
                let invalidEl2Leaf = {
                    el2Leaf: DecodeHexStringToByteArray(el2Leaf),
                    el2HashedLeaf: DecodeHexStringToByteArray(maliciousHashEventLeaf.substring(2, maliciousHashEventLeaf.length)),
                    el2Position: 1,
                    extraRoot: DecodeHexStringToByteArray(extraDataMerkleRoot),
                    extraMerkleProofs: [DecodeHexStringToByteArray("36F5BC29C2E9593F50B0E017700DC775F7F899FEA2FE8CEE8EEA5DDBCD483F0C")],
                }
                let invalidExtraDataRoot = {
                    el2Leaf: DecodeHexStringToByteArray(el2Leaf),
                    el2HashedLeaf: DecodeHexStringToByteArray(el2HashedLeaf.substring(2, el2HashedLeaf.length)),
                    el2Position: 1,
                    extraRoot: DecodeHexStringToByteArray("04D17CC3DD96E88DF05A943EC79DD436F220E84BA9E5F35CACF627CA225424A2"),
                    extraMerkleProofs: [DecodeHexStringToByteArray("36F5BC29C2E9593F50B0E017700DC775F7F899FEA2FE8CEE8EEA5DDBCD483F0C")],
                }
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
                let sigs = [
                    sig1,
                    sig2
                ]
                await expect(chrL2Instance.withdrawRequest(maliciousData, eventProof,
                    DecodeHexStringToByteArray(blockHeader), sigs, el2Proof)
                ).to.be.revertedWith('Postchain: invalid event')
                await expect(chrL2Instance.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader), sigs, invalidEl2Leaf)
                ).to.be.revertedWith('Postchain: invalid el2 extra data')
                await expect(chrL2Instance.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader), sigs, invalidExtraDataRoot)
                ).to.be.revertedWith('Postchain: invalid extra data root')
                await expect(chrL2Instance.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(maliciousBlockHeader), sigs, el2Proof)
                ).to.be.revertedWith('Postchain: invalid block header')
                await expect(chrL2Instance.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader), sigs, maliciousEl2Proof)
                ).to.be.revertedWith('Postchain: invalid el2 extra merkle proof')
                await expect(chrL2Instance.withdrawRequest(data, maliciousEventProof,
                    DecodeHexStringToByteArray(blockHeader), sigs, el2Proof)
                ).to.be.revertedWith('ChrL2: invalid merkle proof')
                await expect(chrL2Instance.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader),
                    [], el2Proof)
                ).to.be.revertedWith('ChrL2: block signature is invalid')

                await expect(chrL2Instance.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader), sigs, el2Proof)
                ).to.emit(chrL2Instance, "WithdrawRequest")
                .withArgs(user.address, tokenAddress, toDeposit)

                await expect(chrL2Instance.withdrawRequest(data, eventProof,
                    DecodeHexStringToByteArray(blockHeader), sigs, el2Proof)
                ).to.be.revertedWith('ChrL2: event hash was already used')

                await expect(chrL2Instance.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    deployer.address)).to.revertedWith("ChrL2: no fund for the beneficiary")

                await expect(chrL2Instance.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address)).to.revertedWith("ChrL2: not mature enough to withdraw the fund")

                // force mining 98 blocks
                for (let i = 0; i < 98; i++) {
                    await ethers.provider.send('evm_mine', [])
                }

                let hashEvent = DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length))

                // directoryNodes can update withdraw request status to pending (emergency case)
                let directoryNode1ChrL2 = new ChrL2__factory(directoryNode1).attach(chrL2Address)
                let directoryNode2ChrL2 = new ChrL2__factory(directoryNode2).attach(chrL2Address)
                let directoryNode3ChrL2 = new ChrL2__factory(directoryNode3).attach(chrL2Address)
                let pendingWithdraw = directoryNode1ChrL2.interface.encodeFunctionData("pendingWithdraw", [hashEvent])                
                await directoryNode1ChrL2.submitTransaction(chrL2Address, BigNumber.from(0), pendingWithdraw)
                var txId = (await directoryNode1ChrL2.transactionCount()).sub(1)
                await directoryNode2ChrL2.confirmTransaction(txId)
                await directoryNode3ChrL2.confirmTransaction(txId)

                // then user cannot withdraw the fund
                await expect(chrL2Instance.withdraw(
                    hashEvent,
                    user.address)).to.be.revertedWith('ChrL2: fund is pending or was already claimed')

                // directoryNodes can set withdraw request status back to withdrawable
                let unpendingWithdraw = directoryNode1ChrL2.interface.encodeFunctionData("unpendingWithdraw", [hashEvent])
                await directoryNode1ChrL2.submitTransaction(chrL2Address, BigNumber.from(0), unpendingWithdraw)
                txId = (await directoryNode1ChrL2.transactionCount()).sub(1)
                await directoryNode2ChrL2.confirmTransaction(txId)
                await directoryNode2ChrL2.revokeConfirmation(txId)
                await directoryNode3ChrL2.confirmTransaction(txId)

                // user still cannot withdraw the fund
                await expect(chrL2Instance.withdraw(
                    hashEvent,
                    user.address)).to.be.revertedWith('ChrL2: fund is pending or was already claimed')

                await directoryNode2ChrL2.confirmTransaction(txId)

                expect(await tokenInstance.balanceOf(user.address)).to.eq(toMint.sub(toDeposit))
                expect(await chrL2Instance._balances(tokenAddress)).to.eq(toDeposit)
                await expect(chrL2Instance.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    deployer.address)).to.be.revertedWith('ChrL2: no fund for the beneficiary')

                // now user can withdraw the fund
                await expect(chrL2Instance.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address))
                .to.emit(chrL2Instance, "Withdrawal")
                .withArgs(user.address, tokenAddress, toDeposit)
                expect(await chrL2Instance._balances(tokenAddress)).to.eq(0)
                expect(await tokenInstance.balanceOf(user.address)).to.eq(toMint)
                await expect(chrL2Instance.withdraw(
                    DecodeHexStringToByteArray(hashEventLeaf.substring(2, hashEventLeaf.length)),
                    user.address)).to.be.revertedWith('ChrL2: fund is pending or was already claimed')
            }
        })
    })

    describe("Mass Exit", async () => {
        it("admins can manage mass exit",async () => {
            let directoryNode1ChrL2 = new ChrL2__factory(directoryNode1).attach(chrL2Address)
            let directoryNode2ChrL2 = new ChrL2__factory(directoryNode2).attach(chrL2Address)
            let directoryNode3ChrL2 = new ChrL2__factory(directoryNode3).attach(chrL2Address)
            expect(await directoryNode1ChrL2.isMassExit()).to.be.false
            let node1 = hashGtvBytes32Leaf(DecodeHexStringToByteArray("977dd435e17d637c2c71ebb4dec4ff007a4523976dc689c7bcb9e6c514e4c795"))
            let node2 = hashGtvBytes32Leaf(DecodeHexStringToByteArray("49e46bf022de1515cbb2bf0f69c62c071825a9b940e8f3892acb5d2021832ba0"))
            let blockRid = postchainMerkleNodeHash([0x7, node1, node2])

            // trigger mass exit
            let triggerMassExit = directoryNode1ChrL2.interface.encodeFunctionData("triggerMassExit", [100, blockRid])
            await directoryNode1ChrL2.submitTransaction(chrL2Address, BigNumber.from(0), triggerMassExit)
            var txId = (await directoryNode1ChrL2.transactionCount()).sub(1)
            await directoryNode2ChrL2.confirmTransaction(txId)
            await directoryNode3ChrL2.confirmTransaction(txId)

            expect(await directoryNode1ChrL2.isMassExit()).to.be.true
            expect((await directoryNode1ChrL2.massExitBlock()).height).to.equal(100)
            expect((await directoryNode1ChrL2.massExitBlock()).blockRid).to.equal(blockRid)
            
            // update mass exit block
            blockRid = postchainMerkleNodeHash([0x7, node2, node1])
            let updateMassExitBlock = directoryNode1ChrL2.interface.encodeFunctionData("updateMassExitBlock", [200, blockRid])
            await directoryNode1ChrL2.submitTransaction(chrL2Address, BigNumber.from(0), updateMassExitBlock)
            var txId = (await directoryNode1ChrL2.transactionCount()).sub(1)
            await directoryNode2ChrL2.confirmTransaction(txId)
            await directoryNode3ChrL2.confirmTransaction(txId)

            expect(await directoryNode1ChrL2.isMassExit()).to.be.true
            expect((await directoryNode1ChrL2.massExitBlock()).height).to.equal(200)
            expect((await directoryNode1ChrL2.massExitBlock()).blockRid).to.equal(blockRid)

            // postpone mass exit
            let postponeMassExit = directoryNode1ChrL2.interface.encodeFunctionData("postponeMassExit")
            await directoryNode1ChrL2.submitTransaction(chrL2Address, BigNumber.from(0), postponeMassExit)
            var txId = (await directoryNode1ChrL2.transactionCount()).sub(1)
            await directoryNode2ChrL2.confirmTransaction(txId)
            await directoryNode3ChrL2.confirmTransaction(txId)
            
            expect(await directoryNode1ChrL2.isMassExit()).to.be.false
        })
    })
})