/* Autogenerated file. Do not edit manually. */
/* tslint:disable */
/* eslint-disable */

import {
  ethers,
  EventFilter,
  Signer,
  BigNumber,
  BigNumberish,
  PopulatedTransaction,
  BaseContract,
  ContractTransaction,
  CallOverrides,
} from "ethers";
import { BytesLike } from "@ethersproject/bytes";
import { Listener, Provider } from "@ethersproject/providers";
import { FunctionFragment, EventFragment, Result } from "@ethersproject/abi";
import { TypedEventFilter, TypedEvent, TypedListener } from "./commons";

interface HashInterface extends ethers.utils.Interface {
  functions: {
    "c_0x9ed2058b(bytes32)": FunctionFragment;
    "hash(bytes32,bytes32)": FunctionFragment;
    "hashGtvBytes32Leaf(bytes32)": FunctionFragment;
    "hashGtvBytes64Leaf(bytes)": FunctionFragment;
    "hashGtvIntegerLeaf(uint256)": FunctionFragment;
  };

  encodeFunctionData(
    functionFragment: "c_0x9ed2058b",
    values: [BytesLike]
  ): string;
  encodeFunctionData(
    functionFragment: "hash",
    values: [BytesLike, BytesLike]
  ): string;
  encodeFunctionData(
    functionFragment: "hashGtvBytes32Leaf",
    values: [BytesLike]
  ): string;
  encodeFunctionData(
    functionFragment: "hashGtvBytes64Leaf",
    values: [BytesLike]
  ): string;
  encodeFunctionData(
    functionFragment: "hashGtvIntegerLeaf",
    values: [BigNumberish]
  ): string;

  decodeFunctionResult(
    functionFragment: "c_0x9ed2058b",
    data: BytesLike
  ): Result;
  decodeFunctionResult(functionFragment: "hash", data: BytesLike): Result;
  decodeFunctionResult(
    functionFragment: "hashGtvBytes32Leaf",
    data: BytesLike
  ): Result;
  decodeFunctionResult(
    functionFragment: "hashGtvBytes64Leaf",
    data: BytesLike
  ): Result;
  decodeFunctionResult(
    functionFragment: "hashGtvIntegerLeaf",
    data: BytesLike
  ): Result;

  events: {};
}

export class Hash extends BaseContract {
  connect(signerOrProvider: Signer | Provider | string): this;
  attach(addressOrName: string): this;
  deployed(): Promise<this>;

  listeners<EventArgsArray extends Array<any>, EventArgsObject>(
    eventFilter?: TypedEventFilter<EventArgsArray, EventArgsObject>
  ): Array<TypedListener<EventArgsArray, EventArgsObject>>;
  off<EventArgsArray extends Array<any>, EventArgsObject>(
    eventFilter: TypedEventFilter<EventArgsArray, EventArgsObject>,
    listener: TypedListener<EventArgsArray, EventArgsObject>
  ): this;
  on<EventArgsArray extends Array<any>, EventArgsObject>(
    eventFilter: TypedEventFilter<EventArgsArray, EventArgsObject>,
    listener: TypedListener<EventArgsArray, EventArgsObject>
  ): this;
  once<EventArgsArray extends Array<any>, EventArgsObject>(
    eventFilter: TypedEventFilter<EventArgsArray, EventArgsObject>,
    listener: TypedListener<EventArgsArray, EventArgsObject>
  ): this;
  removeListener<EventArgsArray extends Array<any>, EventArgsObject>(
    eventFilter: TypedEventFilter<EventArgsArray, EventArgsObject>,
    listener: TypedListener<EventArgsArray, EventArgsObject>
  ): this;
  removeAllListeners<EventArgsArray extends Array<any>, EventArgsObject>(
    eventFilter: TypedEventFilter<EventArgsArray, EventArgsObject>
  ): this;

  listeners(eventName?: string): Array<Listener>;
  off(eventName: string, listener: Listener): this;
  on(eventName: string, listener: Listener): this;
  once(eventName: string, listener: Listener): this;
  removeListener(eventName: string, listener: Listener): this;
  removeAllListeners(eventName?: string): this;

  queryFilter<EventArgsArray extends Array<any>, EventArgsObject>(
    event: TypedEventFilter<EventArgsArray, EventArgsObject>,
    fromBlockOrBlockhash?: string | number | undefined,
    toBlock?: string | number | undefined
  ): Promise<Array<TypedEvent<EventArgsArray & EventArgsObject>>>;

  interface: HashInterface;

  functions: {
    c_0x9ed2058b(
      c__0x9ed2058b: BytesLike,
      overrides?: CallOverrides
    ): Promise<[void]>;

    hash(
      left: BytesLike,
      right: BytesLike,
      overrides?: CallOverrides
    ): Promise<[string]>;

    hashGtvBytes32Leaf(
      value: BytesLike,
      overrides?: CallOverrides
    ): Promise<[string]>;

    hashGtvBytes64Leaf(
      value: BytesLike,
      overrides?: CallOverrides
    ): Promise<[string]>;

    hashGtvIntegerLeaf(
      value: BigNumberish,
      overrides?: CallOverrides
    ): Promise<[string]>;
  };

  c_0x9ed2058b(
    c__0x9ed2058b: BytesLike,
    overrides?: CallOverrides
  ): Promise<void>;

  hash(
    left: BytesLike,
    right: BytesLike,
    overrides?: CallOverrides
  ): Promise<string>;

  hashGtvBytes32Leaf(
    value: BytesLike,
    overrides?: CallOverrides
  ): Promise<string>;

  hashGtvBytes64Leaf(
    value: BytesLike,
    overrides?: CallOverrides
  ): Promise<string>;

  hashGtvIntegerLeaf(
    value: BigNumberish,
    overrides?: CallOverrides
  ): Promise<string>;

  callStatic: {
    c_0x9ed2058b(
      c__0x9ed2058b: BytesLike,
      overrides?: CallOverrides
    ): Promise<void>;

    hash(
      left: BytesLike,
      right: BytesLike,
      overrides?: CallOverrides
    ): Promise<string>;

    hashGtvBytes32Leaf(
      value: BytesLike,
      overrides?: CallOverrides
    ): Promise<string>;

    hashGtvBytes64Leaf(
      value: BytesLike,
      overrides?: CallOverrides
    ): Promise<string>;

    hashGtvIntegerLeaf(
      value: BigNumberish,
      overrides?: CallOverrides
    ): Promise<string>;
  };

  filters: {};

  estimateGas: {
    c_0x9ed2058b(
      c__0x9ed2058b: BytesLike,
      overrides?: CallOverrides
    ): Promise<BigNumber>;

    hash(
      left: BytesLike,
      right: BytesLike,
      overrides?: CallOverrides
    ): Promise<BigNumber>;

    hashGtvBytes32Leaf(
      value: BytesLike,
      overrides?: CallOverrides
    ): Promise<BigNumber>;

    hashGtvBytes64Leaf(
      value: BytesLike,
      overrides?: CallOverrides
    ): Promise<BigNumber>;

    hashGtvIntegerLeaf(
      value: BigNumberish,
      overrides?: CallOverrides
    ): Promise<BigNumber>;
  };

  populateTransaction: {
    c_0x9ed2058b(
      c__0x9ed2058b: BytesLike,
      overrides?: CallOverrides
    ): Promise<PopulatedTransaction>;

    hash(
      left: BytesLike,
      right: BytesLike,
      overrides?: CallOverrides
    ): Promise<PopulatedTransaction>;

    hashGtvBytes32Leaf(
      value: BytesLike,
      overrides?: CallOverrides
    ): Promise<PopulatedTransaction>;

    hashGtvBytes64Leaf(
      value: BytesLike,
      overrides?: CallOverrides
    ): Promise<PopulatedTransaction>;

    hashGtvIntegerLeaf(
      value: BigNumberish,
      overrides?: CallOverrides
    ): Promise<PopulatedTransaction>;
  };
}
