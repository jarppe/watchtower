# watchtower

A simple file/directory watcher library

This is a fork of [ibdknox/watchtower](https://github.com/ibdknox/watchtower). The original implementation
uses polling to detect changes on file system, this version uses the 
[gmethvin/directory-watcher](https://github.com/gmethvin/directory-watcher).

The purpose for this change is to provide much faster notifications for file changes, and consume much
less CPU, especially on large projects.

The public API is the same, so this almost a drop-in replacement (see the changes below). You can just override 
the dependency to `ibdknox/watchtower` with `jarppe/watchtower`.

The status of this is pretty much alpha for now.

[![Clojars Project](https://img.shields.io/clojars/v/jarppe/watchtower.svg)](https://clojars.org/jarppe/watchtower)

## Usage

```clojure
(watcher ["src/" "resources/"]
  (file-filter ignore-dotfiles) ;; add a filter for the files we care about
  (file-filter (extensions :clj :cljs)) ;; filter by extensions
  (on-change #(println "files changed: " %)))
```

## Changes to [ibdknox/watchtower](https://github.com/ibdknox/watchtower)

The original watchtower polls filesystem and detects changes to files based on file timestamp. This version 
uses [gmethvin/directory-watcher](https://github.com/gmethvin/directory-watcher).

1. The `gmethvin/directory-watcher`, and therefore this library, requires JDK 8
1. The `ibdknox/watchtower` calls callback immediately with every file in event, this library does not call
the callback initially. Only the actual changes cause the callback invocation.
1. In `ibdknox/watchtower` the `watchtower.core/watcher` returned a future, this version returns a zero arity fn
that will shutdown watcher when invoked.  

This version also has some tests for public API and adds some annotations to avoid reflection.

## Testing

This version uses [clojure tools-deps](https://clojure.org/guides/getting_started) instead of 
[Leiningen](https://leiningen.org/), so make sure you have clojure CLI installed (on Mac's you can 
just `brew install clojure`).

```bash
clojure -A:test -m kaocha.runner
```

You can run tests on [docker](https://www.docker.com/get-started) container:

```bash
docker run --rm -v ~/.m2:/root/.m2 -v $(pwd):/watchtower -w /watchtower clojure:openjdk-11-tools-deps clojure -A:test -m kaocha.runner
```

You can run the nrepl in docker container too, here's an example that binds nrepl to localhost:6000:

```bash
docker run --rm -v ~/.m2:/root/.m2 -v $(pwd):/watchtower -w /watchtower -p 6000:6000 clojure:openjdk-11-tools-deps clojure -R:dev:test:nrepl -m nrepl.cmdline -b 0.0.0.0 -p 6000
```
 
Instruct your favourite IDE to open an nrepl client to localhost:6000 and hack on. The tests can be run in repl by 
calling:

```clj
(user/run-unit-tests)
```

## License

Copyright (C) 2011 Chris Granger

Distributed under the Eclipse Public License, the same as Clojure.
