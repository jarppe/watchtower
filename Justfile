jdk="11"

help:
  @just --list


#
# Dev:
#


# Check for outdated deps
outdated:
  clojure -Adev:nrepl:test:outdated


# Run tests
test +args='':
  clojure -A:test -m kaocha.runner {{args}}


# Run tests in docker
docker-test +args='':
  docker run --rm -v ~/.m2:/root/.m2 -v $(pwd):/work -w /work clojure:openjdk-{{jdk}}-tools-deps clojure -A:test -m kaocha.runner {{args}}


# Run nrepl in docker
docker-nrepl port='6000':
  docker run --rm -v ~/.m2:/root/.m2 -v $(pwd):/work -w /work -p {{port}}:6000 clojure:openjdk-{{jdk}}-tools-deps clojure -R:dev:test:nrepl -m nrepl.cmdline -b 0.0.0.0 -p 6000


#
# Deploy:
#

make-jar:
  clojure -A:pack mach.pack.alpha.skinny --project-path target/watchtower.jar

deploy: make-jar
  mvn deploy:deploy-file -Dfile=target/watchtower.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml
