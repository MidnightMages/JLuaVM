local function f(...)
    error()
end

--f()

local g = setmetatable({asdf=f,[2]=f,[2.5]=f,[true]=f}, {
    __add=function()
        error()
    end
})
--local y = g + 1

--g["asdf"]()
--g[2]()
--g[2.5]()
--g[true]()

local function x() return "asdf" end
--g[x()]()

local function y() return f end
y()()

local other = f
other()