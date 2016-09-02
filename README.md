# sparqlbye

SPARQLByE (SPARQL By Example) reverse engineering SPARQL queries system and user interface (see the [project website](https://gdiazc.github.io/sparqlbye/))

## Installation

    mvn package
    java -jar sparqlbye-{version}.jar

Then point your browser to `localhost:5555/`. Note that SPARQLByE depends on an external SPARQL endpoint and expects it to be available in port `8890`.
