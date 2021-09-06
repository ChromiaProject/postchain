import { Web3ReactProvider } from "@web3-react/core";
import React, { useState } from "react";
import Connector, { getLibrary } from "./components/Connector";
import { Toaster } from "react-hot-toast";
import { QueryClient, QueryClientProvider } from "react-query";
import { ReactQueryDevtools } from "react-query/devtools";
import ChrL2Contract from "./ChrL2";
import "./App.css";

const chrL2Address = import.meta.env.VITE_CHRL2_ADDRESS

const queryClient = new QueryClient();
function App() {
    const [tokenAddress, setTokenAddress] = useState("");

    return (
        <Web3ReactProvider getLibrary={getLibrary}>
            <QueryClientProvider client={queryClient}>
            <div className="App">
                <Connector />

                <select class="select select-bordered w-full max-w-xs" onChange={(evt) => setTokenAddress(evt.target.value)}>
                    <option disabled selected>Choose your token to deposit</option>
                    <option value="0xe35487517b1bee0e22daf706a82f1d3d1fd963fd">CHR</option>
                    <option value="0x2b203de02ad6109521e09985b3af9b8c62541cd6">OMG</option> 
                    <option value="0xeb8f08a975ab53e34d8a0330e0d34de942c95926">USDC</option>
                    <option value="0x4da8d0795830f75be471f072a034d42c369b5d0a">LINK</option>
                </select>
                <ChrL2Contract chrL2Address={chrL2Address} tokenAddress={tokenAddress} />
            </div>
            <ReactQueryDevtools initialIsOpen={false} />
            <Toaster position="top-right" />            
            </QueryClientProvider>
        </Web3ReactProvider>
    );
}

export default App;