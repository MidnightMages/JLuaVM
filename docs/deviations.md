# Lua / LuaC deviations
This document describes all known cases where JLuaVM deviates from LuaC or the Lua spec itself.
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


## Lua language spec deviations
Similarly, in these cases the lua spec is not followed (meaning if LuaC follows the specification, the behaviour will also differ from LuaC):
- **math.randomseed(seed)**

    This function does not accept two seeds, but instead only one. Similarly it also only returns one number. Extra arguments are ignored.

## Spec extensions
- **_EXT**

    TODO document