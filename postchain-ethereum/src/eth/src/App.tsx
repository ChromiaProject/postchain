import { Web3ReactProvider } from "@web3-react/core";
import React from "react";
import Connector, { getLibrary } from "./components/Connector";
import { Toaster } from "react-hot-toast";
import { QueryClient, QueryClientProvider } from "react-query";
import { ReactQueryDevtools } from "react-query/devtools";
import ChrL2Contract from "./ChrL2";
import "./App.css";

const chrL2Address = import.meta.env.VITE_CHRL2_ADDRESS
const tokenAddress = import.meta.env.VITE_TOKEN_ADDRESS

const queryClient = new QueryClient();
function App() {
    return (
        <Web3ReactProvider getLibrary={getLibrary}>
            <QueryClientProvider client={queryClient}>
            <div className="App">
                <Connector />
                <ChrL2Contract chrL2Address={chrL2Address} tokenAddress={tokenAddress} />
            </div>
            <ReactQueryDevtools initialIsOpen={false} />
            <Toaster position="top-right" />            
            </QueryClientProvider>
        </Web3ReactProvider>
    );
}

export default App;