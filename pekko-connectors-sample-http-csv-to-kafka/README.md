# Apache Pekko Connectors sample

## Fetch CSV via Pekko HTTP and publish the data as JSON to Kafka

This example uses @extref[Pekko HTTP to send the HTTP request](pekko-http:client-side/connection-level.html#opening-http-connections) and Pekko HTTPs primary JSON support via @extref[Spray JSON](pekko-http:common/json-support.html#spray-json-support) (for Scala) or Jackson JSON (for Java) to convert the map into a JSON structure which gets published to a Kafka topic.

Browse the sources at @link:[Github](https://github.com/apache/pekko-connectors-samples/tree/main/pekko-connectors-sample-http-csv-to-kafka) { open=new }.

To try out this project clone @link:[the Pekko-Connectors Samples repository](https://github.com/apache/pekko-connectors-samples) { open=new } and find it in the `pekko-connectors-sample-http-csv-to-kafka` directory.
