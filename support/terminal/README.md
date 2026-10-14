# Terminal

`:terminal` contains shared ANSI terminal primitives.

It provides the `AnsiTerminal` I/O contract, with a JLine raw-mode
implementation for play and a plain stdout one for scripted runs, ANSI escape
constants (`AnsiCodes`), and raw key and line decoding (`AnsiInput`, which maps
arrow keys to the keypad movement digits). The direct terminal runtime and the
browser ANSI runtime both rely on this module for terminal-shaped IO behavior.

This module should remain small. Game rules, screen layout decisions, and
browser rendering do not belong here.
