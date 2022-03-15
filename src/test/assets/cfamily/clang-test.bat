@echo OFF

:: Now I want to write a message to stderr
:: (which in the parent is piped to stderr.log)
echo #include <...> search starts here: 1>&2
echo End of search list. 1>&2

:: Writes a message to stdout
:: (which in the parent is piped to stdout.log)
echo #define __cplusplus 201703
