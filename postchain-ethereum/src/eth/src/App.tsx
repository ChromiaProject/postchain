import { Web3ReactProvider } from "@web3-react/core";
import React from "react";
import { getLibrary } from "./components/Demo";
import "./App.css";
import Demo from "./components/Demo";

function App() {
    return (
        <Web3ReactProvider getLibrary={getLibrary}>
            <div className="App"><Demo />
            </div>
        </Web3ReactProvider>
    );
}

export default App;