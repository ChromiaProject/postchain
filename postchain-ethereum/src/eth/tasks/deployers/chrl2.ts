import { task } from "hardhat/config";
import {ChrL2, ChrL2__factory, MerkleProof, MerkleProof__factory, Hash, Hash__factory, EC, EC__factory, Postchain__factory, Postchain} from "../../src/types";

task("deploy:ChrL2")
  .addOptionalParam('directory', 'derectory node')
  .addOptionalParam('app', 'app node')
  .addFlag('verify', 'Verify contracts at Etherscan')
  .setAction(async ({ verify, directory, app}, hre) => {
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
    const ec: EC = <EC>await ecdsaFactory.deploy();

    // deploy postchain
    const postchainFactory: Postchain__factory = await hre.ethers.getContractFactory("Postchain", {
        libraries: {
            EC: ec.address,
            Hash: hash.address,
            MerkleProof: merkleProof.address,
        }
    })
    const postchain: Postchain = <Postchain>await postchainFactory.deploy();

    // deploy ChrL2 smart contract
    const chrL2Factory: ChrL2__factory = await hre.ethers.getContractFactory("ChrL2", {
        libraries: {
            Postchain: postchain.address,
            MerkleProof: merkleProof.address,
        }
    })
    const directoryNode = directory === undefined ? [] : getNodes(directory);
    const appNode = app === undefined ? [] : getNodes(app);
    const chrL2: ChrL2 = <ChrL2>await chrL2Factory.deploy(directoryNode, appNode);
    await chrL2.deployed();
    console.log("ChrL2 deployed to: ", chrL2.address);

    if (verify) {
        // We need to wait a little bit to verify the contract after deployment
        await delay(30000);
        await hre.run("verify:verify", {
            address: chrL2.address,
            constructorArguments: [
                directoryNode,
                appNode
            ],
            libraries: {
                Postchain: postchain.address,
                MerkleProof: merkleProof.address,
            },
        });
    }
  });

function delay(ms: number) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function getNodes(nodes: string) {
    return nodes.split(',');
}