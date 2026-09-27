# MQTT Bridge for the WG2 APIs

> **Note:**
>
>  This is not associated with WG2 in any way, and is not an official WG2 product.
>
>  More information about the WG2 APIs can be found at https://docs.wgtwo.com

This is a simple bridge to allow you to use the WG2 APIs over MQTT.

You may connect to this bridge using your phone number without the `+` prefix as your username.

Credentials will be sent as a SMS once access is granted.

A running version of this bridge is available at `mqtt-bridge.haxxor.xyz`.

### Inbox
All events from WG2 will be posted to `{USERNAME}/inbox/*` topics.

### Outbox
The outbox is used to send to the WG2 APIs 

## Supported topics

| Topic                      | Description                            |
|----------------------------|----------------------------------------|
| `{USERNAME}/inbox/sms`     | Receive SMS messages                   |
| `{USERNAME}/outbox/sms`    | Send SMS messages                      |
| `{USERNAME}/inbox/call`    | Receive phone call notifications       |
| `{USERNAME}/inbox/consent` | Receive notification about new consent |

## Usage

> **Note**:
> 
> All examples assume your phone number is `+47 99999999`.

### Receive SMS messages

To receive SMS messages, subscribe to `4799999999/inbox/sms` or `4799999999/inbox/+`.

```json
{
  "metadata": {
    "timestamp": "2023-06-19T10:32:20Z"
  },
  "sms": {
    "from": "+1234567890",
    "to": "+4799999999",
    "content": "💜"
  }
}
```

### Send SMS messages
To send SMS messages, publish to `4799999999/outbox/sms` with the following payload.
`from` must be your own number:

```json
{
  "sms": {
    "from": "+4799999999",
    "to": "+1234567890",
    "content": "💜"
  }
}
```

## Bridge using Mosquitto
The following example will bridge traffic between a local Mosquitto broker and MQTT Bridge.

Local prefix `wg2/` will be mapped to `{YOUR USERNAME}/` on the server.

```
listener 1883
allow_anonymous true

connection wg2
address mqtt-bridge.haxxor.xyz:8883
cleansession true
bridge_insecure false
bridge_capath /etc/ssl/certs/
remote_username {USERNAME}
remote_password {YOUR PASSWORD}
# Disabled as we do not have access to the $SYS tree on the remote broker
notifications_local_only true
bridge_protocol_version mqttv50
try_private true
topic # both 1 wg2/ {USERNAME}/
```

## Running the server

### Create config

```shell
cat <<EOF > my-config.yaml
wg2:
  clientId: "${CLIENT_ID}"
  clientSecret: "${CLIENT_SECRET}"
  eventQueue: "wg2mqtt"
  # Optional, this is the default
  apiTarget: "api.shamrock.wgtwo.com:443"
mqtt:
  ports:
    ws: 9001
    mqtt: 1883
sqlite:
  path: "mqttbridge.sqlite"
# Optional, this is the default
metrics:
  port: 9090
EOF
```

### Run

The toolchain (Java 25, Maven) is pinned in `mise.toml`:

```shell
mise install
mise exec -- mvn package
mise exec -- java -jar target/wg2mqtt-1.0-SNAPSHOT.jar my-config.yaml
```

Or `./run.sh my-config.yaml`, or build the container with `docker build .`.

### Metrics

Prometheus metrics are served on `:9090/metrics` (`metrics.port`):

| Metric                                | Description                                                   |
|---------------------------------------|---------------------------------------------------------------|
| `wg2_stream_connected{stream}`        | 1 while the WG2 event stream is subscribed                    |
| `wg2_stream_errors_total{stream}`     | Stream failures (each is followed by a reconnect)             |
| `wg2_events_received_total{stream,type}` | Events from WG2, by type (`sms`, `call`, `added`, …, `ignored`) |
| `wg2_sms_sent_total{result}`          | SMS sent through WG2 (`success`/`failure`)                    |
| `mqtt_clients_connected`              | Connected MQTT clients                                        |
| `mqtt_authentications_total{result}`  | MQTT logins (`success`/`failure`)                             |
| `mqtt_authorizations_denied_total`    | Publish/subscribe attempts outside the user's own topics      |
| `mqtt_messages_received_total{result}`| Messages from clients (`accepted`/`rejected`/`invalid`)       |
| `mqtt_messages_published_total`       | Events published to clients                                   |
| `mqtt_bytes_{received,sent}_total`    | MQTT traffic                                                  |
| `mqtt_bridge_users`                   | Registered users                                              |

Plus the standard JVM and process metrics.

### Reset a user's password

Users are created when they grant consent, and get a random password by SMS.
To set a new password for an existing user, pass it on stdin:

```shell
java -jar target/wg2mqtt-1.0-SNAPSHOT.jar set-password mqttbridge.sqlite 4799999999 < new-password.txt
```
