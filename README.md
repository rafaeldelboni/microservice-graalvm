# microservice-graalvm
Clojure Microservice Boilerplate: http-kit, java-http-clj, ruuter, interceptor, schema, postgresql and tests. Made to be compiled in GraalVM

## TODO

### POC

- [x] Using http-kit/ruuters
- [x] Add interceptor into the stack 
- [x] Add create before interceptor that coerce/convert request parameters (path & body)
- [x] Add create after interceptor that coerce/convert response body
- [x] Do a http request using http-client (parse request/response)
- [x] Add PostgreSQL call
- [ ] Add create after interceptor that validate response body
- [ ] Add create before interceptor that validate request parameters (path & body)
- [x] Compile using GraalVM

## Features

### System
- [schema](https://github.com/plumatic/schema) Types and Schemas
- [component](https://github.com/stuartsierra/component) System Lifecycle and Dependencies
- [http-kit](https://github.com/http-kit/http-kit) Http Server
- [ruuter](https://github.com/askonomm/ruuter) Http Routes System 
- [java-http-clj](https://github.com/schmee/java-http-clj) Http Client
- [interceptor](https://github.com/exoscale/interceptor) Http Client
- [cheshire](https://github.com/dakrone/cheshire) JSON encoding
- [aero](https://github.com/juxt/aero) Configuration file and enviroment variables manager
- [timbre](https://github.com/ptaoussanis/timbre) Logging library
- [next-jdbc](https://github.com/seancorfield/next-jdbc) JDBC-based layer to access databases
- [honeysql](https://github.com/seancorfield/honeysql) SQL as Clojure data structures
- [depstar](https://github.com/seancorfield/depstar) Generates Uberjars for releases

### Tests & Checks
- [kaocha](https://github.com/lambdaisland/kaocha) Test runner
- [kaocha-cloverage](https://github.com/lambdaisland/kaocha-cloverage) Kaocha plugin for code coverage reports
- [schema-generators](https://github.com/plumatic/schema-generators) Data generation and generative testing
- [state-flow](https://github.com/nubank/state-flow) Testing framework for integration tests
- [matcher-combinators](https://github.com/nubank/matcher-combinators) Assertions in data structures
- [pg-embedded-clj](https://github.com/Bigsy/pg-embedded-clj) Embedded PostgreSQL for integration tests
- [clojure-lsp](https://github.com/clojure-lsp/clojure-lsp/) Code Format, Namespace Check and Diagnosis

## Directory Structure
```
./
├── .clj-kondo -- clj-kondo configuration and classes
├── .github
│   └── workflows -- Github workflows folder.
├── docker -- docker and docker-compose files for the database
├── resources -- Application resources assets folder and configuration files.
│   └── migrations -- Current database schemas, synced on service startup.
├── src -- Library source code and headers.
│   ├── parenthesin -- Source for common utilities and helpers.
│   └── microservice_graalvm -- Source for the service example (wallet).
└── test -- Test source code.
    ├── integration -- Integration tests source (uses state-flow).
    │   ├── parenthesin -- Tests for common utilities and helpers.
    │   └── microservice_graalvm -- Tests for service example (wallet).
    └── unit -- Unity tests source (uses clojure.test).
        ├── parenthesin -- Tests for common utilities.
        └── microservice_graalvm -- Tests for service example (wallet).
```

## Related

### Similar Projects
- [vloth/ts-microservice-boilerplate](https://github.com/vloth/ts-microservice-boilerplate)

### Forks
- [parenthesin/microservice-boilerplate](https://github.com/parenthesin/microservice-boilerplate)
- [parenthesin/microservice-boilerplate-malli](https://github.com/parenthesin/microservice-boilerplate-malli)

## License
This is free and unencumbered software released into the public domain.  
For more information, please refer to <http://unlicense.org>
