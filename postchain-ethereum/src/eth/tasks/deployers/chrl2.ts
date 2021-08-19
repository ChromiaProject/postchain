import { task } from "hardhat/config";
import { TaskArguments } from "hardhat/types";

import {ChrL2, ChrL2__factory, MerkleProof, MerkleProof__factory, Hash, Hash__factory, EC, EC__factory} from "../../typechain";

task("deploy:ChrL2")
  .setAction(async function (taskArguments: TaskArguments, { ethers }) {
    // deploy hash lib
    const hashFactory: Hash__factory = await ethers.getContractFactory("Hash");
    const hash: Hash = <Hash>await hashFactory.deploy()

    // deploy merkle proof lib
    const merleProofFactory: MerkleProof__factory = await ethers.getContractFactory("MerkleProof", {
        libraries: {
            Hash: hash.address,
        }
    });
    const merkleProof: MerkleProof = <MerkleProof>await merleProofFactory.deploy()

    // deploy ECDSA lib
    const ecdsaFactory: EC__factory = await ethers.getContractFactory("EC");
    const ec: EC = <EC>await ecdsaFactory.deploy()

    // deploy ChrL2 smart contract
    const chrL2Factory: ChrL2__factory = await ethers.getContractFactory("ChrL2", {
        libraries: {
            EC: ec.address,
            Hash: hash.address,
            MerkleProof: merkleProof.address,
        }
    });
    const chrL2: ChrL2 = <ChrL2>await chrL2Factory.deploy();
    await chrL2.deployed();
    console.log("ChrL2 deployed to: ", chrL2.address);
  });