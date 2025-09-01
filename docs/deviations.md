# Lua / LuaC deviations
This document describes all known cases where JLuaVM deviates from LuaC (the official implementation of a LUA interpreter) or the Lua specification itself.
Please report any cases that are not listed yet by opening an issue ticket.

## LuaC deviations
In the following cases JLuaVM behaves differently than LuaC, but we still fulfill the Lua specification:
- **string indexing** (`("someString")[someIndex]`)

    In LuaC an index operation on a string always returns nil. This seems inconsistent and instead we throw an error like when indexing any other non-indexable type.

- **length of string** (`#("someString")`)

    We intentionally return the number of characters instead of the length in bytes.

- **assert**

    Throws a slightly different error message that indicates the value of a boolean argument rather than stating it was a boolean.

- **ipairs**

    In our case, ipairs only allows iterating over tables. LuaC would return nil when operating on a string and error when passing a type that is neither a string nor a table. For robustness we throw on all non-table arguments as it seems very unlikely that a string is passed intentionally.

- **pairs**

    The order in which the items are returned differs from LuaC.

- **table.insert**

    LuaC seems to show different behaviour depending on if a table is a sequence or an actual dictionary, so when adding a key to a sequence suddenly the insertion behaviour is changed. In our case insertion stays consistent.

- **table.remove**

    Differs slightly as the table-length operator behaves differently.

- **parsing and then printing hex-float numbers**

    JLuaVM appears to offer a couple more digits of precisions than LuaC when outputting numbers.

- **stacktraces are formatted slightly differently**

    Due to the lack of a `loadfile` function, the chunkname is directly fed into stacktraces, allowing for specifying filenames, etc. without being surrounded by quotes.


## Lua language specification deviations
Similarly, in these cases the lua specification (spec) is not followed (meaning if LuaC follows the spec, the behaviour will also differ from LuaC):
- **math.randomseed(seed)**

    This function does not accept two seeds, but instead only one. Similarly it also only returns one number. Extra arguments are ignored.

## Spec extensions

- **Type Extension Methods via `_ENV["_EXT"]`**

    'Vanilla' Lua supports setting type-metatables using `debug.setmetatable`, e.g. for the string type. This, to our knowledge, however only works on a global level, so you cannot easily configure this on a per-environment setting.

    We have therefore added support for what we call "Type Extension Methods", as they behave similarly to extension methods in C#. If an index operation fails and would normally result in an error, we try to perform a lookup in a subtable of _EXT. 

    For example, the following code `"test":sub(1,3)` is processed as follows: 
    - "test" is a string, hence is not indexable, nor can contain a metatable, so we immediately check for an extension function (or throw an index error otherwise)
    - So we now check for `_ENV["_EXT"]["string"]["sub"]`, which by default does exist, as `_ENV._EXT.string` is set to a table that contains most of the string-library functions.

    In more general terms we access `_ENV["_EXT][NAME OF TYPE][NAME OF FUNCTION]` (where `_ENV` is often equivalent to `_G`). If no match is found, we simply raise the usual error.

