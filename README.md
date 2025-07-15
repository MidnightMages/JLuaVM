# JLuaVM

A Lua 5.4 runtime written from scratch in Java, with the intent of supporting pausing, saving and reloading of the execution state.

Differences in behaviour compared to the Lua specification itself and LuaC can be found [here](./docs/deviations.md), though these should not be noticeable in most Lua scripts.

## Current state

TODO explain what is missing

## Contributing

If you wish to contribute, feel free to clone the repository and propose changes. You should however be careful when modifying code that works directly with the LuaVM_RT instance (essentially any code containing `vm.`). 

Most beneficial would likely be adding new tests that test specific edgecases and ensure compliance with the [Lua 5.4 language specification](https://www.lua.org/manual/5.4/manual.html) and LuaC.