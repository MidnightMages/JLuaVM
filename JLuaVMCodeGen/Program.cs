using System.Reflection;
using System.Text.RegularExpressions;

namespace JLuaVMCodeGen;

internal partial class Program {

    private static string jluavmRootDir = Assembly.GetExecutingAssembly().Location.Split(Path.DirectorySeparatorChar)[..^5].Aggregate((a, b) => $"{a}{Path.DirectorySeparatorChar}{b}");

    private static void GenFile(string @namespace, Func<string> contents) {
        Console.WriteLine($"Generating file {@namespace.Split('.')[^1]}$$.java");
        File.WriteAllText(Path.Combine(jluavmRootDir, "src", @namespace.Replace('.', Path.DirectorySeparatorChar)) + "$$.java", $"package {@namespace[..@namespace.LastIndexOf('.')]};\n" + contents.Invoke());
    }

    private static string GetBinaryOperationSnippetXY(string opName, string directCode) {
        var rv = "";
        foreach (var varname in "xy") {
            rv += $$"""
        if (!{{varname}}.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) {{varname}});
            var f = tbl.getMtFunc("{{opName}}");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }

""";
        }
        rv += $$"""
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        if (!x.isNumber() || !y.isNumber()) {
            throw new LuaTypeError("attempted to perform operation '%s {{opName}} %s'".formatted(x.getType().fancyName, y.getType().fancyName));
        }
        return {{directCode.TrimEnd(';')}};
""";
        return EndOfLineComments().Replace(rv, string.Empty);
    }

    private static void Main(string[] args) {

        // reference https://www.lua.org/manual/5.4/manual.html#2.4
        string FCallNumNum(string funcName) => $"((LuaNumber$) x).{funcName}((LuaNumber$) y)";
        var binaryOperations = new Dictionary<string, string>() {
            {"add", FCallNumNum("add") },
            {"sub", FCallNumNum("sub") },

            {"mul", FCallNumNum("mul") },
            {"div", FCallNumNum("div") },
            {"idiv", FCallNumNum("idiv") },
            {"mod", FCallNumNum("mod") },


            {"pow", FCallNumNum("pow") },
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
{{GetGeneratedBodies()}}
}
""");

        string GetGeneratedBodies() => binaryOperations.Select((kv) => $$"""
    public static LuaVariable$ IL__{{kv.Key}}(LuaVariable$ x, LuaVariable$ y) {
{{GetBinaryOperationSnippetXY(kv.Key, kv.Value)}}
    }
""").Aggregate((a, b) => a + "\n" + b);
    }

    [GeneratedRegex("//.*?$")]
    private static partial Regex EndOfLineComments();
}