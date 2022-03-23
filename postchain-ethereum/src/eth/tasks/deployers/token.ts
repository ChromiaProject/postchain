import { task } from "hardhat/config";
import {TestToken, TestToken__factory} from "../../src/types";

task("deploy:token")
  .addFlag('verify', 'Verify contracts at Etherscan')
  .setAction(async ({ verify }, hre) => {
    const tokenFactory: TestToken__factory = await hre.ethers.getContractFactory("TestToken");
    const token: TestToken = <TestToken>await tokenFactory.deploy()
    await token.deployed();
    console.log("token deployed to: ", token.address);

    if (verify) {
        // We need to wait a little bit to verify the contract after deployment
        await delay(30000);
        await hre.run("verify:verify", {
            address: token.address,
            constructorArguments: [],
            libraries: {},
        });
    }
  });

function delay(ms: number) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}