package dev.asdf00.jluavm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

public class Util {
    public static String[] expandOptions(String snippet) {
        return expandOptions(new String[]{snippet});
    }

    public static String[] expandOptions(String[] snippets) {
        return Arrays.stream(snippets).flatMap(s -> {
            if (!s.contains("§"))
                return Stream.of(s);

            var expanded = new ArrayList<String>();
            expanded.add(s);
            boolean anyExpanded;
            do {
                anyExpanded = false;
                for (int i = 0; i < expanded.size(); i++) {
                    var curr = expanded.get(i);
                    var firstGroupStartIndex = curr.indexOf("§");
                    if (firstGroupStartIndex == -1)
                        continue;

                    anyExpanded = true;
                    var firstGroupEndIndex = curr.indexOf("§", firstGroupStartIndex + 1);
                    var firstGroup = curr.substring(firstGroupStartIndex + 1, firstGroupEndIndex);
                    var replacements = firstGroup.split("\\|", -1);
                    boolean isFirst = true;
                    for (var rep : replacements) {
                        var repString = curr.substring(0, firstGroupStartIndex) + rep + curr.substring(firstGroupEndIndex + 1);
                        if (isFirst) {
                            isFirst = false;
                            expanded.set(i, repString);
                        } else {
                            expanded.add(repString);
                        }
                    }
                }
            } while (anyExpanded);
            return expanded.stream();
        }).toArray(String[]::new);
    }
}
