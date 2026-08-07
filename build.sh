#!/bin/sh

# comment this out when testing (usually)
# may also want to just build manually? Then maybe can just do 
# in "business logic script" and not have to worry about this
# pulling in the dep
lein jdeb

fakeroot -- sh _build_needs_root.sh