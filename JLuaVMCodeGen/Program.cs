using System.Reflection;
using System.Text.RegularExpressions;

namespace JLuaVMCodeGen;

internal partial class Program {

    private static string jluavmRootDir = Assembly.GetExecutingAssembly().Location.Split(Path.DirectorySeparatorChar)[..^5].Aggregate((a, b) => $"{a}{Path.DirectorySeparatorChar}{b}");

    private static void GenFile(string @namespace, Func<string> contents) {
        Console.WriteLine($"Generating file {@namespace.Split('.')[^1]}$$.java");
        File.WriteAllText(Path.Combine(jluavmRootDir, "src", @namespace.Replace('.', Path.DirectorySeparatorChar)) + "$$.java", $"package {@namespace[..@namespace.LastIndexOf('.')]};\n" + contents.Invoke());
    }

    private static string GetBinaryOperationSnippetXY(string opName, string directCode, CoercionType coercionMethod) {
        var rv = "";
        var requiredType = coercionMethod switch {
            CoercionType.ToNum => "Number",
            CoercionType.ToStr => "String",
            _ => throw new NotImplementedException()
        };
        // step1: try coercion
        if (coercionMethod != CoercionType.None) {
            foreach (var varname in "xy") {
                rv += $$"""
        {{varname}} = IL___COERCETo{{coercionMethod switch { CoercionType.ToNum => "Num", CoercionType.ToStr => "Str", _ => throw new NotImplementedException()}}}({{varname}});

""";
            }
        }

        // step2: if x or y isnt of the target type, look for a metatable
        rv += $$"""        
        if (!x.is{{requiredType}}() || !y.is{{requiredType}}()) { // if any of the args isnt of the required type after coercion, look for a metatable

""";
        foreach (var varname in "xy") {
            rv += $$"""
            if ({{varname}}.isTable()){
                var f = ((LuaTable$) {{varname}}).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }

""";
        }
        rv += $$"""
            throw new LuaTypeError("attempted to perform operation '%s {{opName}} %s'".formatted(x.getType().fancyName, y.getType().fancyName));            
        }
        assert x instanceof Lua{{requiredType}}$;
        assert y instanceof Lua{{requiredType}}$;
        return {{directCode.TrimEnd(';')}};
""";
        return EndOfLineComments().Replace(rv, string.Empty);
    }

    private static void Main(string[] args) {

        // reference https://www.lua.org/manual/5.4/manual.html#2.4
        string FCallNumNum(string funcName) => $"((LuaNumber$) x).{funcName}((LuaNumber$) y)";
        string FCallStrStr(string funcName) => $"((LuaString$) x).{funcName}((LuaString$) y)";
        var binaryOperations = new List<MethodDef>() {
            new("concat", FCallStrStr("concat"), CoercionType.ToStr),

            new("add", FCallNumNum("add"), CoercionType.ToNum),
            new("sub", FCallNumNum("sub"), CoercionType.ToNum),

            new("mul", FCallNumNum("mul"), CoercionType.ToNum),
            new("div", FCallNumNum("div"), CoercionType.ToNum),
            new("idiv", FCallNumNum("idiv"), CoercionType.ToNum),
            new("mod", FCallNumNum("mod"), CoercionType.ToNum),


            new("pow", FCallNumNum("pow"), CoercionType.ToNum),
        };


        GenFile("dev.asdf00.jluavm.parsing.ir.operations.BinaryOpNode", () => {

            return $$"""
import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.parsing.ir.Node;

import java.util.Objects;

public class BinaryOpNode$$ extends Node {
    protected final Node x;
    protected final Node y;
    private final TokenType tokenType;

    public BinaryOpNode$$(Node x, Node y, TokenType tokenType) {
        this.x = x;
        this.y = y;
        this.tokenType = tokenType;
    }

    @Override
    public String generate() {
        return P("BinaryOpNode_RTIMPL$$.IL__%s(%s, %s)".formatted(Objects.requireNonNull(tokenType.metatableFuncName), x.generate(), y.generate()));
    }
}
""";
        });
        GenFile("dev.asdf00.jluavm.rtutils.BinaryOpNode_RTIMPL", () => $$"""
import dev.asdf00.jluavm.types.*;
import dev.asdf00.jluavm.exceptions.runtime.*;

public class BinaryOpNode_RTIMPL$$ {
    public static LuaVariable$ IL___COERCEToNum(LuaVariable$ a){
        return a; // TODO return a LuaNumber$ if coercion is possible, otherwise return the argument a
    }

    public static LuaVariable$ IL___COERCEToStr(LuaVariable$ a){
        return a; // TODO return a LuaString$ if coercion is possible, otherwise return the argument a
    }
{{GetGeneratedBodies()}}
}
""");

        string GetGeneratedBodies() => binaryOperations.Select((kv) => $$"""

    public static LuaVariable$ IL__{{kv.FuncName}}(LuaVariable$ x, LuaVariable$ y) {
{{GetBinaryOperationSnippetXY(kv.FuncName, kv.DirectSnippet, kv.CoercionKind)}}
    }
""").Aggregate((a, b) => a + "\n" + b);
    }

    [GeneratedRegex("//.*?$")]
    private static partial Regex EndOfLineComments();
}

internal record struct MethodDef(string FuncName, string DirectSnippet, CoercionType CoercionKind) { }