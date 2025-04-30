#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <ESP32Servo.h>

#define SERVICE_UUID        "14eff093-8234-4b9c-89ee-6929ad5222e1"
#define CHARACTERISTIC_UUID "ed011481-be35-4609-b0d3-1f57019b2af2"

#define SERVO_PIN 8
#define PULSE_MIN 900
#define PULSE_MAX 2100

Servo servo;
uint16_t servoPosition = (PULSE_MIN + PULSE_MAX) / 2;

BLECharacteristic* pServoCharacteristic = nullptr;

// Template to parse raw BLE data into a variable
template<typename T>
bool parseValue(BLECharacteristic* pChar, T &value) {
    if (pChar->getLength() >= sizeof(T)) {
        memcpy(&value, pChar->getData(), sizeof(T));
        return true;
    }
    return false;
}

// Callback for handling BLE writes
class ServoCharacteristicCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
    uint16_t pos;
    if (parseValue(pChar, pos)) {
        Serial.println("Characteristic write received:");
        Serial.println(pos);
        servoPosition = pos;
        pChar->setValue((uint8_t*)&servoPosition, sizeof(servoPosition));
        pChar->notify();  // Notify client of new value
    } else {
        Serial.println("Error: Not enough data for uint16_t.");
    }
  }
};

// Callback for handling BLE connection events
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    Serial.println("Client connected.");
  }

  void onDisconnect(BLEServer* pServer) override {
    Serial.println("Client disconnected. Restarting advertising...");
    pServer->getAdvertising()->start();
  }
};

void setup() {
  Serial.begin(9600);

  BLEDevice::init("MayWindTunnel");

  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks()); // Handle connection events

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pServoCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_READ |
    BLECharacteristic::PROPERTY_WRITE |
    BLECharacteristic::PROPERTY_NOTIFY
  );

  pServoCharacteristic->setCallbacks(new ServoCharacteristicCallbacks());
  pServoCharacteristic->setValue((uint8_t*)&servoPosition, sizeof(servoPosition));

  pService->start();

  BLEAdvertising *pAdvertising = pServer->getAdvertising();
  pAdvertising->start();
  Serial.println("Started advertising");

  servo.attach(SERVO_PIN, PULSE_MIN, PULSE_MAX);
}

void loop() {
  servo.write(servoPosition);
  Serial.print("Servo position: ");
  Serial.println(servoPosition);
  delay(20);
}
