# Pekko Connectors Samples

### @extref:[Download all files from an FTP server](ftp-to-file:index.html)
This example uses Pekko Connectors FTP to download files from an FTP server.

### @extref:[Fetch CSV via HTTP and publish to Kafka](http-csv-to-kafka:index.html)
This example uses Pekko HTTP to send the HTTP request and Pekko HTTP's JSON support to convert the map into a JSON structure which gets published to a Kafka topic.

### @extref:[Examples using JMS](jms:index.html)
This example uses receives JMS messages with Pekko Connectors JMS and sends those to different technologies.

### @extref:[Read from a database and write to Elasticsearch](jdbc-to-elasticsearch:index.html)
This example uses Pekko Connectors Slick to read from a relational database and write the data to Elasticsearch.

### @extref:[Read from Kafka and write to Elasticsearch](kafka-to-elasticsearch:index.html)
This example uses Pekko Connectors Kafka to read from an Apache Kafka topic, parse the messages as JSON and write the data to Elasticsearch, committing offsets to Kafka after successfully updating Elasticsearch.

### @extref:[Read from Kafka and publish to websockets](kafka-to-websocket-clients:index.html)
Clients may connect via websockets and will receive data read from a Kafka topic.

### @extref:[Read files from a directory and write to Elasticsearch](file-to-elasticsearch:index.html)
This example uses Pekko Connectors File to watch for new files created in a directory and then tails them (similar to Elasticsearch's `logstash` utility). The tailed logs are parsed and indexed in Elasticsearch using Pekko Connectors Elasticsearch.

### @extref:[Rotate logs, zip and write to SFTP server](rotate-logs-to-ftp:index.html)
Read a stream of data and store it zipped in rotating files on an SFTP server.

### @extref:[Subscribe to MQTT and produce to Kafka](mqtt-to-kafka:index.html)
Subscribe to an MQTT topic with @extref[Pekko Connectors MQTT](pekko-connectors:/mqtt.html), group a few values and publish the aggregate to a Kafka topic with @extref[Pekko Connectors Kafka](pekko-connectors-kafka:).

### @link:[Amazon SQS](https://github.com/apache/pekko-connectors-samples/tree/main/pekko-connectors-sample-sqs-java) { open=new }
Listen to an Amazon SQS topic, enrich the message via calling an actor, publish a new message to SQS and acknowledge/delete the original message. (Java only)

### @link:[MQTT topic triggers file download which is uploaded to AWS S3](https://github.com/apache/pekko-connectors-samples/tree/main/pekko-connectors-sample-mqtt-http-to-s3-java) { open=new }

Listen to a MQTT topic, download from the URL passed in the received message, and upload the data from that address to AWS S3. (Java only)
