# Terminal

`:terminal` contains shared ANSI terminal primitives.

It provides low-level terminal support such as ANSI escape constants and JLine
integration. The direct terminal runtime and the browser ANSI runtime both rely
on this module for terminal-shaped IO behavior.

This module should remain small. Game rules, screen layout decisions, and
browser rendering do not belong here.
