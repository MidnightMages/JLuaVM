#!/bin/bash

cd "$(dirname "$0")"
cd JLuaVMCodeGen
echo "Genning code..."
dotnet run
cd ..
echo "Done"