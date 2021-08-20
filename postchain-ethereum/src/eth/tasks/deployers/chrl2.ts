import { task } from "hardhat/config";
import {ChrL2, ChrL2__factory, MerkleProof, MerkleProof__factory, Hash, Hash__factory, EC, EC__factory} from "../../typechain";

task("deploy:ChrL2")
  .addFlag('verify', 'Verify contracts at Etherscan')
  .setAction(async ({ verify }, hre) => {
    // deploy hash lib
    const hashFactory: Hash__factory = await hre.ethers.getContractFactory("Hash");
    const hash: Hash = <Hash>await hashFactory.deploy()

    // deploy merkle proof lib
    const merkleProofFactory: MerkleProof__factory = await hre.ethers.getContractFactory("MerkleProof", {
        libraries: {
            Hash: hash.address,
        }
    });
    const merkleProof: MerkleProof = <MerkleProof>await merkleProofFactory.deploy()

    // deploy ECDSA lib
    const ecdsaFactory: EC__factory = await hre.ethers.getContractFactory("EC");
    const ec: EC = <EC>await ecdsaFactory.deploy()

    // deploy ChrL2 smart contract
    const chrL2Factory: ChrL2__factory = await hre.ethers.getContractFactory("ChrL2", {
        libraries: {
            EC: ec.address,
            Hash: hash.address,
            MerkleProof: merkleProof.address,
        }
    });
    const chrL2: ChrL2 = <ChrL2>await chrL2Factory.deploy();
    await chrL2.deployed();
    console.log("ChrL2 deployed to: ", chrL2.address);

    if (verify) {
        // We need to wait a little bit to verify the contract after deployment
        delay(12000);
        await hre.run("verify:verify", {
            address: chrL2.address,
            libraries: {
                EC: ec.address,
                Hash: hash.address,
                MerkleProof: merkleProof.address,
            }
        });
    }
  });

function delay(ms: number) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}