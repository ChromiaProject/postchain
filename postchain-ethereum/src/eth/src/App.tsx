import { Web3ReactProvider } from "@web3-react/core";
import React from "react";
import Connector, { getLibrary } from "./components/Connector";
import "./App.css";

function App() {
    return (
        <Web3ReactProvider getLibrary={getLibrary}>
            <div className="App"><Connector />
            </div>
        </Web3ReactProvider>
    );
}

export default App;