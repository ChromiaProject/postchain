// SPDX-License-Identifier: GPL-3.0-only
pragma solidity >=0.6.0 <=0.8.7;

interface ERC20 {
    function transfer(address _to, uint _value) external;
    function transferFrom(address _from, address _to, uint _value) external;
}
