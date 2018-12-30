# watchtower

A simple file/directory watcher library

This is a fork of [ibdknox/watchtower](https://github.com/ibdknox/watchtower). The original implementation
uses polling to detect changes on file system, this version uses the fancy new
[WatchService](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchService.html) available in JDK 7.

The public API is the same, so this is a drop-in replacement. You can just override the dependency to
`ibdknox/watchtower` with `jarppe/watchtower`.

The status of this is pretty much alpha for now.

## Usage

```clojure
(watcher ["src/" "resources/"]
  (file-filter ignore-dotfiles) ;; add a filter for the files we care about
  (file-filter (extensions :clj :cljs)) ;; filter by extensions
  (on-change #(println "files changed: " %)))
```

## Changes to [ibdknox/watchtower](https://github.com/ibdknox/watchtower)

The original watchtower polls filesystem and detects changes to files based on file
timestamp. This version uses JDK 7 [WatchService](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchService.html)
for change detections.

This causes following changes:

1. This version requires JDK 7 or later
2. This version can be much faster on some systems (Linux)
3. This version can be much slower on some systems (Mac)

This version also has some tests for public API and adds some annotations to avoid reflection.

On systems where the WatchService in your JDK is implemented using file system level hooks, this version
can be much faster and require much less CPU, especially on large projects with lots of files to watch. 
Linux is this kind of system.

On other systems the WatchService is implemented using polling just like original ibdknox/watchtower, but the 
polling frequency can not be adjusted. The polling frequency is JDK implementation specific, and it
seems to be pretty slow (around 5 - 8 seconds). Mac's are this kind of systems.

As for Windows, I have no idea at this time.

If you're on Mac you probably should keep on using original watchtower.

## Testing

This version uses [clojure tools-deps](https://clojure.org/guides/getting_started) instead of [Leiningen](https://leiningen.org/),
so make sure you have clojure CLI installed (on Mac's you can just `brew install clojure`).

```bash
clojure -A:test -m kaocha.runner
```

You can run tests on Mac too, but that's pretty slow (see above). How ever, if you run your tests on [docker](https://www.docker.com/get-started) 
container you can enjoy the magnificent speed of Linux implementation of the WatchService.

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
