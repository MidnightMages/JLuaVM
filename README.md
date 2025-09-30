# JLuaVM

A Lua 5.4 runtime written from scratch in Java, with the intent of supporting pausing, saving and reloading of the execution state.

Differences in behaviour compared to the Lua specification itself and LuaC can be found [here](./docs/deviations.md), though these should not be noticeable in most Lua scripts.

## Current state

TODO explain what is missing

## Usage
Despite the fact that this Lua VM is primarily developed for the Minecraft mod Advanced Computers<!-- todo add link -->, we aim to also provide support and bugfixes for features that other projects (including other computer mods) are making use of, though adding completely new features may be slow.

Thus, feel free to use this and open up an issue ticket if something does not work.

## Acknowledgement
The DelayedJavaCompiler class, in the package `dev.asdf00.jluavm.internals`, is largely taken from Java Object Oriented Reflection library (jOOR) which, itself, 
is distributed under the Apache License Version 2.0 with the source code available [on Github](https://github.com/jOOQ/jOOR).

## Contributing

If you wish to contribute, feel free to clone the repository and propose changes. You should however be careful when modifying code that works directly with the LuaVM_RT instance (essentially any code containing `vm.`). 

Most beneficial would likely be adding new tests that test specific edgecases and ensure compliance with the [Lua 5.4 language specification](https://www.lua.org/manual/5.4/manual.html) and LuaC.
