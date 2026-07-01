#!/bin/bash
awk '
BEGIN { count = 0 }
{
    for(i=1; i<=length($0); i++) {
        c = substr($0, i, 1)
        if (c == "{") count++
        if (c == "}") count--
    }
    print NR, count, $0
}' app/src/main/java/com/example/MainActivity.kt > brace_counts.txt
