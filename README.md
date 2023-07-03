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
To send SMS messages, publish to `4799999999/outbox/sms` with the following payload:

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
mqtt:
  ports:
    ws: 9001
    mqtt: 1883
users:
  - phone: "${PHONE}"
    password: "${PASSWORD}"
sqlite:
  path: "mqttbridge.sqlite"
EOF
```

### Run

```shell
mvn clean package
java -jar target/wg2mqtt-1.0-SNAPSHOT.jar my-config.yaml
```
