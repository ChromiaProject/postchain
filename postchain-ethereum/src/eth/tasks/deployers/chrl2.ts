import { task } from "hardhat/config";
import { ChrL2, ChrL2__factory } from "../../src/types";
import { HardhatRuntimeEnvironment } from "hardhat/types";

task("deploy:ChrL2")
  .addOptionalParam('app', 'app node')
  .addFlag('verify', 'Verify contracts at Etherscan')
  .setAction(async ({ verify, app}, hre) => {
    // deploy ChrL2 smart contract
    const chrL2Factory: ChrL2__factory = await hre.ethers.getContractFactory("ChrL2")
    const appNode = app === undefined ? [] : getNodes(app);

    const chrL2: ChrL2 = <ChrL2>await hre.upgrades.deployProxy(chrL2Factory, [appNode]);
    await chrL2.deployed();
    console.log("ChrL2 deployed to: ", chrL2.address);
    const proxyAdmin = await hre.upgrades.erc1967.getAdminAddress(chrL2.address);
    console.log("Proxy admin address is: ", proxyAdmin);

    if (verify) {
        await verifyContract(hre, chrL2.address);
    }
  });

task("prepare:ChrL2")
    .addParam('address', '')
    .setAction(async ({ address }, hre ) => {
        const chrL2Factory: ChrL2__factory = await hre.ethers.getContractFactory("ChrL2");
        const upgrade = await hre.upgrades.prepareUpgrade(address, chrL2Factory);
        console.log("New logic contract of ChrL2 has been prepared for upgrade at: ", upgrade);
    });

task("upgrade:ChrL2")
    .addParam('address', '')
    .addFlag('verify', 'Verify contracts at Etherscan')
    .setAction(async ({ address, verify }, hre) => {
        const chrL2Factory: ChrL2__factory = await hre.ethers.getContractFactory("ChrL2");
        await hre.upgrades.upgradeProxy(address, chrL2Factory);
        console.log("ChrL2 has been upgraded");

        if (verify) {
            await verifyContract(hre, address);
        }
    });

async function verifyContract(hre: HardhatRuntimeEnvironment, proxyAddress: string) {
    // We need to wait a little bit to verify the contract after deployment
    const implementationAddress = await hre.upgrades.erc1967.getImplementationAddress(proxyAddress);
    console.log("Verifying logic contract deployed at: " + implementationAddress + ". This may take some time.");
    await delay(60000);

    await hre.run("verify:verify", {
        address: implementationAddress,
    });
}

function delay(ms: number) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function getNodes(nodes: string) {
    return nodes.split(',');
}