# WG2MqttBridge

## Usage

cat <<EOF > my-config.yaml
wg2:
  clientId: "${CLIENT_ID}"
  clientSecret: "${CLIENT_SECRET}"
  eventQueue: "wg2mqtt"
mqtt:
  ports:
    ws: 9999
users:
  - phone: "${PHONE}"
    password: "${PASSWORD}"
EOF

```shell
mvn clean package
java -jar target/wg2mqtt-1.0-SNAPSHOT.jar my-config.yaml
```

# Add executor for event handling
