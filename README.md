# A text editor based on Kilo in Java

Run the program:

```commandline
java Main.java [filename]
```

Supports

- Ctrl-S: Save
- Ctrl-Q: Quit
- Ctrl-F: Search
- Syntax highlighting: C

The program was only for learning purposes. It has only been tested on MacOS.

## Ideas for Improvement

- Better data structures for storing the
  text: [rope](https://en.wikipedia.org/wiki/Rope_(data_structure)), [gap buffer](https://en.wikipedia.org/wiki/Gap_buffer), [piece table](https://en.wikipedia.org/wiki/Piece_table).
- Redo/undo
- Line numbers
- More file types and highlight support
- Audo indent
- word wrapping: [memento](https://en.wikipedia.org/wiki/Memento_pattern), [command](https://en.wikipedia.org/wiki/Command_pattern).
- Multi-platform support

## References

- [Kilo](https://github.com/antirez/kilo?tab=readme-ov-file) by antirez
- [Build Your own Text Editor](https://viewsourcecode.org/snaptoken/kilo/index.html)
- [Challenging projects every programmer should try](https://austinhenley.com/blog/challengingprojects.html)
- [Marco Codes](https://www.youtube.com/@MarcoCodes) text editor series.